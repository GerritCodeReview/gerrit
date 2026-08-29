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
import com.google.common.collect.Iterables;
import com.google.common.collect.MoreCollectors;
import com.google.common.collect.SetMultimap;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.ParentCommitData;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.index.IndexConfig;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Provides information about parent commits of patch sets.
 *
 * <p>Instances are not singletons so that each {@link RevisionJson} request/formatting operation
 * receives a fresh instance with an isolated request-scoped cache. This prevents stale parent data
 * across requests while enabling request-scoped caching and batch prefetching across revisions.
 */
public class ParentDataProvider {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Provider<InternalChangeQuery> queryProvider;
  private final IndexConfig indexConfig;
  private final Map<ObjectId, Optional<ChangeData>> parentChangeCache = new ConcurrentHashMap<>();
  private final Map<ParentDataCacheKey, ParentCommitData> parentCommitDataCache =
      new ConcurrentHashMap<>();

  @Inject
  public ParentDataProvider(Provider<InternalChangeQuery> queryProvider, IndexConfig indexConfig) {
    this.queryProvider = queryProvider;
    this.indexConfig = indexConfig;
  }

  /**
   * Batches index queries for all specified parent commit IDs across revisions, populating the
   * cache to prevent N+1 sequential index round-trips.
   */
  public void prefetch(Collection<ObjectId> parentCommitIds) {
    if (parentCommitIds.isEmpty()) {
      return;
    }
    Set<ObjectId> toQuery =
        parentCommitIds.stream()
            .filter(id -> !parentChangeCache.containsKey(id))
            .collect(Collectors.toSet());
    if (toQuery.isEmpty()) {
      return;
    }
    int batchSize = indexConfig.maxTerms() - 1;
    for (List<ObjectId> batch : Iterables.partition(toQuery, batchSize)) {
      List<ChangeData> changeDataList = queryProvider.get().byCommits(batch);
      Set<ObjectId> batchSet = new HashSet<>(batch);
      SetMultimap<ObjectId, ChangeData> changesByCommit = HashMultimap.create();
      for (ChangeData cd : changeDataList) {
        for (PatchSet ps : cd.patchSets()) {
          if (batchSet.contains(ps.commitId())) {
            changesByCommit.put(ps.commitId(), cd);
          }
        }
      }
      for (ObjectId parentCommitId : batch) {
        Set<ChangeData> matches = changesByCommit.get(parentCommitId);
        if (matches.size() > 1) {
          logger.atWarning().log(
              "Found more than one change associated with parent revision %s. Found"
                  + " changes %s.",
              parentCommitId.name(),
              matches.stream().map(ChangeData::getId).collect(ImmutableList.toImmutableList()));
          parentChangeCache.put(parentCommitId, Optional.empty());
        } else if (matches.size() == 1) {
          parentChangeCache.put(parentCommitId, Optional.of(matches.iterator().next()));
        } else {
          parentChangeCache.put(parentCommitId, Optional.empty());
        }
      }
    }
  }

  /**
   * Returns data about a specific {@code revCommit}, specifically whether it's merged in a {@code
   * targetBranch}, or if it's a patch-set commit of some Gerrit change otherwise. This can be used
   * to get more information of parent commits of patch-sets.
   */
  public ParentCommitData get(
      Project.NameKey project, Repository repo, ObjectId parentCommitId, String targetBranch) {
    ParentDataCacheKey cacheKey = new ParentDataCacheKey(parentCommitId, targetBranch);
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

    boolean inTargetBranch = isMergedInTargetBranch(project, repo, parentCommitId, targetBranch);
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
      Project.NameKey project, Repository repo, ObjectId parentCommitId, String targetBranch) {
    try (RevWalk rw = new RevWalk(repo)) {
      Ref targetBranchRef = repo.exactRef(targetBranch);
      if (targetBranchRef != null) {
        return rw.isMergedInto(
            rw.parseCommit(parentCommitId), rw.parseCommit(targetBranchRef.getObjectId()));
      }
    } catch (IOException e) {
      logger.atWarning().withCause(e).log(
          "Failed to check if parent commit %s (project: %s) is merged into target branch %s",
          parentCommitId.name(), project, targetBranch);
    }
    return false;
  }

  /**
   * Returns {@link ParentCommitData} if there is a change associated with {@code parentCommitId}.
   */
  private Optional<ParentCommitData> getFromGerritChange(
      Project.NameKey project, ObjectId parentCommitId, String targetBranch) {
    Optional<ChangeData> singleDataOpt = parentChangeCache.get(parentCommitId);
    if (singleDataOpt == null) {
      List<ChangeData> changeData = queryProvider.get().byCommit(parentCommitId.name());
      if (changeData.size() > 1) {
        logger.atWarning().log(
            "Found more than one change associated with parent revision %s (project: %s). Found"
                + " changes %s.",
            parentCommitId.name(),
            project.get(),
            changeData.stream().map(ChangeData::getId).collect(ImmutableList.toImmutableList()));
      }
      singleDataOpt = changeData.size() == 1 ? Optional.of(changeData.get(0)) : Optional.empty();
      parentChangeCache.put(parentCommitId, singleDataOpt);
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

  private static final class ParentDataCacheKey {
    private final ObjectId commitId;
    private final String targetBranch;

    ParentDataCacheKey(ObjectId commitId, String targetBranch) {
      this.commitId = commitId;
      this.targetBranch = targetBranch;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof ParentDataCacheKey)) {
        return false;
      }
      ParentDataCacheKey that = (ParentDataCacheKey) o;
      return Objects.equals(commitId, that.commitId)
          && Objects.equals(targetBranch, that.targetBranch);
    }

    @Override
    public int hashCode() {
      return Objects.hash(commitId, targetBranch);
    }
  }
}
