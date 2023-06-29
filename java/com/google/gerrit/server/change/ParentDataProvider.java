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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.MoreCollectors;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.ParentCommitData;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.ReachabilityChecker;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

@Singleton
public class ParentDataProvider {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Provider<InternalChangeQuery> queryProvider;

  @Inject
  public ParentDataProvider(Provider<InternalChangeQuery> queryProvider) {
    this.queryProvider = queryProvider;
  }

  /**
   * Returns data about a specific {@code revCommit}, specifically whether it's merged in a {@code
   * targetBranch}, or if it's a patch-set commit of some Gerrit change otherwise. This can be used
   * to get more information of parent commits of patch-sets.
   */
  public ParentCommitData get(
      Project.NameKey project, Repository repo, ObjectId parentCommitId, String targetBranch) {
    boolean inTargetBranch = isMergedInTargetBranch(project, repo, parentCommitId, targetBranch);
    Optional<ParentCommitData> fromGerritChange =
        getFromGerritChange(project, parentCommitId, targetBranch);
    if (fromGerritChange.isEmpty()) {
      return ParentCommitData.builder()
          .branchName(Optional.of(targetBranch))
          .commitId(Optional.of(parentCommitId))
          .isMergedInTargetBranch(inTargetBranch)
          .autoBuild();
    }
    return fromGerritChange
        .map(f -> f.toBuilder().isMergedInTargetBranch(inTargetBranch).autoBuild())
        .get();
  }

  /** Returns true if the parent commit {@code parentCommitId} is merged in the target branch. */
  private boolean isMergedInTargetBranch(
      Project.NameKey project, Repository repo, ObjectId parentCommitId, String targetBranch) {
    try (RevWalk rw = new RevWalk(repo);
        ObjectReader reader = repo.newObjectReader()) {
      Ref targetBranchRef = repo.exactRef(targetBranch);
      if (targetBranchRef == null) {
        return false;
      }
      RevCommit parent = rw.parseCommit(parentCommitId);
      RevCommit targetBranchCommit = rw.parseCommit(targetBranchRef.getObjectId());
      ReachabilityChecker checker = reader.createReachabilityChecker(rw);
      Optional<RevCommit> unreachable =
          checker.areAllReachable(
              ImmutableList.of(parent), ImmutableList.of(targetBranchCommit).stream());
      return unreachable.isEmpty();
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
    List<ChangeData> changeData = queryProvider.get().byCommit(parentCommitId.name());
    if (changeData.size() != 1) {
      logger.atWarning().log(
          "Did not find a single change associated with parent revision %s (project: %s). Found changes %s.",
          parentCommitId.name(),
          project.get(),
          changeData.stream().map(ChangeData::getId).collect(ImmutableList.toImmutableList()));
      return Optional.empty();
    }
    ChangeData singleData = changeData.get(0);
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
            .autoBuild());
  }
}
