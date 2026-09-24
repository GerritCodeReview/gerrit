// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.server.submit;

import static com.google.gerrit.server.project.ProjectCache.noSuchProject;

import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.CodeReviewCommit.CodeReviewRevWalk;
import com.google.gerrit.server.git.MergeUtil;
import com.google.gerrit.server.git.MergeUtilFactory;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.Set;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevFlag;

/** Dry run of a submit strategy. */
public class SubmitDryRun {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  static class Arguments {
    final Repository repo;
    final CodeReviewRevWalk rw;
    final MergeUtil mergeUtil;
    final MergeSorter mergeSorter;

    Arguments(Repository repo, CodeReviewRevWalk rw, MergeUtil mergeUtil, MergeSorter mergeSorter) {
      this.repo = repo;
      this.rw = rw;
      this.mergeUtil = mergeUtil;
      this.mergeSorter = mergeSorter;
    }
  }

  private final ProjectCache projectCache;
  private final MergeUtilFactory mergeUtilFactory;
  private final Provider<InternalChangeQuery> queryProvider;

  @Inject
  SubmitDryRun(
      ProjectCache projectCache,
      MergeUtilFactory mergeUtilFactory,
      Provider<InternalChangeQuery> queryProvider) {
    this.projectCache = projectCache;
    this.mergeUtilFactory = mergeUtilFactory;
    this.queryProvider = queryProvider;
  }

  public boolean run(
      @Nullable CurrentUser caller,
      SubmitType submitType,
      Repository repo,
      CodeReviewRevWalk rw,
      BranchNameKey destBranch,
      ObjectId tip,
      ObjectId toMerge,
      Set<RevCommit> alreadyAccepted)
      throws NoSuchProjectException, IOException {
    CodeReviewCommit tipCommit = rw.parseCommit(tip);
    CodeReviewCommit toMergeCommit = rw.parseCommit(toMerge);
    RevFlag canMerge = rw.newFlag("CAN_MERGE");
    toMergeCommit.add(canMerge);
    Arguments args =
        new Arguments(
            repo,
            rw,
            mergeUtilFactory.create(getProject(destBranch)),
            new MergeSorter(
                caller,
                rw,
                alreadyAccepted,
                canMerge,
                queryProvider,
                ImmutableSet.of(toMergeCommit)));

    switch (submitType) {
      case CHERRY_PICK:
        return CherryPick.dryRun(args, tipCommit, toMergeCommit);
      case FAST_FORWARD_ONLY:
        return FastForwardOnly.dryRun(args, tipCommit, toMergeCommit);
      case MERGE_ALWAYS:
        return MergeAlways.dryRun(args, tipCommit, toMergeCommit);
      case MERGE_IF_NECESSARY:
        return MergeIfNecessary.dryRun(args, tipCommit, toMergeCommit);
      case REBASE_IF_NECESSARY:
        return RebaseIfNecessary.dryRun(args, repo, tipCommit, toMergeCommit);
      case REBASE_ALWAYS:
        return RebaseAlways.dryRun(args, repo, tipCommit, toMergeCommit);
      case INHERIT:
      default:
        String errorMsg = "No submit strategy for: " + submitType;
        logger.atSevere().log("%s", errorMsg);
        throw new StorageException(errorMsg);
    }
  }

  private ProjectState getProject(BranchNameKey branch) throws NoSuchProjectException {
    return projectCache.get(branch.project()).orElseThrow(noSuchProject(branch.project()));
  }
}
