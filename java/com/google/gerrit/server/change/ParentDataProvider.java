// Copyright (C) 2023 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.server.change;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.MoreCollectors;
import com.google.common.collect.SetMultimap;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.ParentCommitData;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Provides information about parent commits of patch sets.
 *
 * <p>Instances are not singletons so that each {@link RevisionJson} formatting operation receives a
 * fresh instance with an isolated request-scoped cache. This prevents stale parent data across
 * requests while enabling request-scoped memoization and batch prefetching across revisions.
 * Internal caches use {@link ConcurrentHashMap} so cache lookups are thread-safe if a provider
 * instance is shared across concurrent formatting tasks within a request (note that any caller-
 * supplied {@link RevWalk} remains non-thread-safe and must be confined to the calling thread).
 */
public class ParentDataProvider {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private record ProjectCommitKey(Project.NameKey project, ObjectId commitId) {}

  private record ParentDataCacheKey(
      Project.NameKey project, ObjectId commitId, String targetBranch) {}

  private final Provider<InternalChangeQuery> queryProvider;
  private final Map<ProjectCommitKey, Optional<ChangeData>> parentChangeCache =
      new ConcurrentHashMap<>();
  private final Map<ParentDataCacheKey, ParentCommitData> parentCommitDataCache =
      new ConcurrentHashMap<>();
  private final Map<BranchNameKey, Optional<ObjectId>> targetBranchRefCache =
      new ConcurrentHashMap<>();

  @Inject
  public ParentDataProvider(Provider<InternalChangeQuery> queryProvider) {
    this.queryProvider = queryProvider;
  }

  /**
   * Batches index queries for all specified parent commit IDs in {@code project} across revisions,
   * populating the request-scoped cache to prevent N+1 sequential index round-trips.
   */
  public void prefetch(Project.NameKey project, Collection<ObjectId> parentCommitIds) {
    if (parentCommitIds.isEmpty()) {
      return;
    }
    Set<ObjectId> toQuery = new LinkedHashSet<>();
    for (ObjectId id : parentCommitIds) {
      if (!parentChangeCache.containsKey(new ProjectCommitKey(project, id))) {
        toQuery.add(id);
      }
    }
    if (toQuery.isEmpty()) {
      return;
    }
    InternalChangeQuery query =
        queryProvider.get().setRequestedFields(ChangeField.CHANGE_SPEC, ChangeField.PATCH_SET_SPEC);
    List<ChangeData> changeDataList;
    if (toQuery.size() == 1) {
      changeDataList = query.byProjectCommit(project, toQuery.iterator().next());
    } else {
      ImmutableList<String> hashes =
          toQuery.stream().map(ObjectId::name).collect(ImmutableList.toImmutableList());
      changeDataList = query.byProjectCommits(project, hashes);
    }
    SetMultimap<ObjectId, ChangeData> changesByCommit = HashMultimap.create();
    for (ChangeData cd : changeDataList) {
      if (cd == null || cd.change() == null) {
        continue;
      }
      for (PatchSet ps : cd.patchSets()) {
        if (toQuery.contains(ps.commitId())) {
          changesByCommit.put(ps.commitId(), cd);
        }
      }
    }
    for (ObjectId parentCommitId : toQuery) {
      Set<ChangeData> matches = changesByCommit.get(parentCommitId);
      if (matches.size() > 1) {
        logger.atWarning().log(
            "Found more than one change associated with parent revision %s (project: %s). Found"
                + " changes %s.",
            parentCommitId.name(),
            project.get(),
            matches.stream().map(ChangeData::getId).collect(ImmutableList.toImmutableList()));
      }
      Optional<ChangeData> singleDataOpt =
          matches.size() == 1 ? Optional.of(matches.iterator().next()) : Optional.empty();
      parentChangeCache.put(new ProjectCommitKey(project, parentCommitId), singleDataOpt);
    }
  }

  /**
   * Returns data about a specific {@code revCommit}, specifically whether it's merged in a {@code
   * targetBranch}, or if it's a patch-set commit of some Gerrit change otherwise. This can be used
   * to get more information of parent commits of patch-sets.
   */
  public ParentCommitData get(
      Project.NameKey project, Repository repo, ObjectId parentCommitId, String targetBranch) {
    return get(project, repo, /* rw= */ null, parentCommitId, targetBranch);
  }

  /**
   * Returns data about a specific {@code parentCommitId}, reusing the provided {@link RevWalk} when
   * checking target branch reachability.
   */
  public ParentCommitData get(
      Project.NameKey project,
      Repository repo,
      @Nullable RevWalk rw,
      ObjectId parentCommitId,
      String targetBranch) {
    ParentDataCacheKey cacheKey = new ParentDataCacheKey(project, parentCommitId, targetBranch);
    ParentCommitData cached = parentCommitDataCache.get(cacheKey);
    if (cached != null) {
      return cached;
    }

    Optional<ParentCommitData> fromGerritChange =
        getFromGerritChange(project, parentCommitId, targetBranch);
    if (fromGerritChange.isPresent()) {
      ParentCommitData result = fromGerritChange.get();
      parentCommitDataCache.put(cacheKey, result);
      return result;
    }

    boolean inTargetBranch =
        isMergedInTargetBranch(project, repo, rw, parentCommitId, targetBranch);
    ParentCommitData result =
        ParentCommitData.builder()
            .branchName(Optional.of(targetBranch))
            .commitId(Optional.of(parentCommitId))
            .isMergedInTargetBranch(inTargetBranch)
            .autoBuild();
    parentCommitDataCache.put(cacheKey, result);
    return result;
  }

  /** Returns true if the parent commit {@code parentCommitId} is merged in the target branch. */
  private boolean isMergedInTargetBranch(
      Project.NameKey project,
      Repository repo,
      @Nullable RevWalk rw,
      ObjectId parentCommitId,
      String targetBranch) {
    try {
      Optional<ObjectId> targetBranchTip = getTargetBranchTip(project, repo, targetBranch);
      if (targetBranchTip.isEmpty()) {
        return false;
      }
      if (rw != null) {
        return rw.isMergedInto(
            rw.parseCommit(parentCommitId), rw.parseCommit(targetBranchTip.get()));
      }
      try (RevWalk localRw = new RevWalk(repo)) {
        return localRw.isMergedInto(
            localRw.parseCommit(parentCommitId), localRw.parseCommit(targetBranchTip.get()));
      }
    } catch (IOException e) {
      logger.atWarning().withCause(e).log(
          "Failed to check if parent commit %s (project: %s) is merged into target branch %s",
          parentCommitId.name(), project, targetBranch);
    }
    return false;
  }

  private Optional<ObjectId> getTargetBranchTip(
      Project.NameKey project, Repository repo, String targetBranch) throws IOException {
    BranchNameKey branchKey = BranchNameKey.create(project, targetBranch);
    Optional<ObjectId> cached = targetBranchRefCache.get(branchKey);
    if (cached != null) {
      return cached;
    }
    Ref targetBranchRef = repo.exactRef(targetBranch);
    Optional<ObjectId> tip =
        targetBranchRef != null && targetBranchRef.getObjectId() != null
            ? Optional.of(targetBranchRef.getObjectId())
            : Optional.empty();
    targetBranchRefCache.put(branchKey, tip);
    return tip;
  }

  /**
   * Returns {@link ParentCommitData} if there is a change associated with {@code parentCommitId}.
   */
  private Optional<ParentCommitData> getFromGerritChange(
      Project.NameKey project, ObjectId parentCommitId, String targetBranch) {
    ProjectCommitKey projectCommitKey = new ProjectCommitKey(project, parentCommitId);
    Optional<ChangeData> singleDataOpt = parentChangeCache.get(projectCommitKey);
    if (singleDataOpt == null) {
      List<ChangeData> changeData =
          queryProvider
              .get()
              .setRequestedFields(ChangeField.CHANGE_SPEC, ChangeField.PATCH_SET_SPEC)
              .byProjectCommit(project, parentCommitId);
      if (changeData.size() > 1) {
        logger.atWarning().log(
            "Found more than one change associated with parent revision %s (project: %s). Found"
                + " changes %s.",
            parentCommitId.name(),
            project.get(),
            changeData.stream().map(ChangeData::getId).collect(ImmutableList.toImmutableList()));
      }
      singleDataOpt =
          changeData.size() == 1 && changeData.get(0) != null && changeData.get(0).change() != null
              ? Optional.of(changeData.get(0))
              : Optional.empty();
      parentChangeCache.put(projectCommitKey, singleDataOpt);
    }
    if (singleDataOpt.isEmpty()) {
      return Optional.empty();
    }
    ChangeData singleData = singleDataOpt.get();
    int patchSetNumber =
        singleData.patchSets().stream()
            .filter(p -> p.commitId().equals(parentCommitId))
            .collect(MoreCollectors.onlyElement())
            .number();
    return Optional.of(
        ParentCommitData.builder()
            .branchName(Optional.of(targetBranch))
            .commitId(Optional.of(parentCommitId))
            .changeKey(Optional.of(singleData.change().getKey()))
            .changeNumber(Optional.of(singleData.getId().get()))
            .patchSetNumber(Optional.of(patchSetNumber))
            .changeStatus(Optional.of(singleData.change().getStatus()))
            .isMergedInTargetBranch(
                patchSetNumber == singleData.change().currentPatchSetId().get()
                    && singleData.change().isMerged())
            .autoBuild());
  }
}
