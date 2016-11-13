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

package com.google.gerrit.server.git.strategy;

import com.google.common.collect.FluentIterable;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.CodeReviewCommit.CodeReviewRevWalk;
import com.google.gerrit.server.git.IntegrationException;
import com.google.gerrit.server.git.MergeSorter;
import com.google.gerrit.server.git.MergeUtil;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevFlag;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Dry run of a submit strategy. */
public class SubmitDryRun {
  private static final Logger log = LoggerFactory.getLogger(SubmitDryRun.class);

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

  public static Iterable<ObjectId> getAlreadyAccepted(Repository repo) throws IOException {
    return FluentIterable.from(repo.getRefDatabase().getRefs(Constants.R_HEADS).values())
        .append(repo.getRefDatabase().getRefs(Constants.R_TAGS).values())
        .transform(Ref::getObjectId);
  }

  public static Set<RevCommit> getAlreadyAccepted(Repository repo, RevWalk rw) throws IOException {
    Set<RevCommit> accepted = new HashSet<>();
    addCommits(getAlreadyAccepted(repo), rw, accepted);
    return accepted;
  }

  public static void addCommits(Iterable<ObjectId> ids, RevWalk rw, Collection<RevCommit> out)
      throws IOException {
    for (ObjectId id : ids) {
      RevObject obj = rw.parseAny(id);
      if (obj instanceof RevCommit) {
        out.add((RevCommit) obj);
      }
    }
  }

  private final ProjectCache projectCache;
  private final MergeUtil.Factory mergeUtilFactory;

  @Inject
  SubmitDryRun(ProjectCache projectCache, MergeUtil.Factory mergeUtilFactory) {
    this.projectCache = projectCache;
    this.mergeUtilFactory = mergeUtilFactory;
  }

  public boolean run(
      SubmitType submitType,
      Repository repo,
      CodeReviewRevWalk rw,
      Branch.NameKey destBranch,
      ObjectId tip,
      ObjectId toMerge,
      Set<RevCommit> alreadyAccepted)
      throws IntegrationException, NoSuchProjectException, IOException {
    CodeReviewCommit tipCommit = rw.parseCommit(tip);
    CodeReviewCommit toMergeCommit = rw.parseCommit(toMerge);
    RevFlag canMerge = rw.newFlag("CAN_MERGE");
    toMergeCommit.add(canMerge);
    Arguments args =
        new Arguments(
            repo,
            rw,
            mergeUtilFactory.create(getProject(destBranch)),
            new MergeSorter(rw, alreadyAccepted, canMerge));

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
        return RebaseIfNecessary.dryRun(args, tipCommit, toMergeCommit);
      case REBASE_ALWAYS:
        return RebaseAlways.dryRun(args, tipCommit, toMergeCommit);
      default:
        String errorMsg = "No submit strategy for: " + submitType;
        log.error(errorMsg);
        throw new IntegrationException(errorMsg);
    }
  }

  private ProjectState getProject(Branch.NameKey branch) throws NoSuchProjectException {
    ProjectState p = projectCache.get(branch.getParentKey());
    if (p == null) {
      throw new NoSuchProjectException(branch.getParentKey());
    }
    return p;
  }
}
