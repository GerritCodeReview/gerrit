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
  public Optional<ParentCommitData> get(
      Project.NameKey project, Repository repo, ObjectId commitId, String targetBranch) {
    Optional<ParentCommitData> parentData =
        getFromTargetBranch(project, repo, commitId, targetBranch);
    if (parentData.isPresent()) {
      return parentData;
    }
    return getFromGerritChange(project, commitId);
  }

  /**
   * Returns {@link ParentCommitData} if the parent commit is reachable from the target branch, or
   * {@link Optional#empty()} otherwise.
   */
  private Optional<ParentCommitData> getFromTargetBranch(
      Project.NameKey project, Repository repo, ObjectId parentId, String targetBranch) {
    try {
      Ref targetBranchRef = repo.exactRef(targetBranch);
      if (targetBranchRef == null) {
        return Optional.empty();
      }
      RevWalk rw = new RevWalk(repo);
      RevCommit parent = rw.parseCommit(parentId);
      RevCommit targetBranchCommit = rw.parseCommit(targetBranchRef.getObjectId());
      ReachabilityChecker checker = repo.newObjectReader().createReachabilityChecker(rw);
      Optional<RevCommit> unreachable =
          checker.areAllReachable(
              ImmutableList.of(parent), ImmutableList.of(targetBranchCommit).stream());
      if (unreachable.isEmpty()) {
        return Optional.of(
            ParentCommitData.builder()
                .branchName(Optional.of(targetBranch))
                .commitId(Optional.of(parentId))
                .autoBuild());
      }
    } catch (IOException e) {
      logger.atWarning().withCause(e).log(
          "Failed to check if parent commit %s (project: %s) is merged into target branch %s",
          parentId.name(), project, targetBranch);
    }
    return Optional.empty();
  }

  /**
   * Returns {@link ParentCommitData} if the parent commit is a patch-set of another Gerrit change,
   * or {@link Optional#empty()} otherwise.
   */
  private Optional<ParentCommitData> getFromGerritChange(
      Project.NameKey project, ObjectId parentId) {
    List<ChangeData> changeData = queryProvider.get().byCommit(parentId.name());
    if (changeData.size() != 1) {
      logger.atWarning().log(
          "Did not find a single change associated with parent revision %s (project: %s)",
          parentId.name(), project.get());
      return Optional.empty();
    }
    ChangeData singleData = changeData.get(0);
    int patchSetNumber =
        singleData.patchSets().stream()
            .filter(p -> p.commitId().equals(parentId))
            .collect(MoreCollectors.onlyElement())
            .number();
    return Optional.of(
        ParentCommitData.builder()
            .commitId(Optional.of(parentId))
            .changeKey(Optional.of(singleData.change().getKey()))
            .changeNumber(Optional.of(singleData.getId().get()))
            .patchSetNumber(Optional.of(patchSetNumber))
            .changeStatus(Optional.of(singleData.change().getStatus()))
            .autoBuild());
  }
}
