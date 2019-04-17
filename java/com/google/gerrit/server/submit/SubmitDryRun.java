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

import static java.util.stream.Collectors.toSet;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.CodeReviewCommit.CodeReviewRevWalk;
import com.google.gerrit.server.git.MergeUtil;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevFlag;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;

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

  public static Set<ObjectId> getAlreadyAccepted(Repository repo) throws IOException {
    return Streams.concat(
            repo.getRefDatabase().getRefsByPrefix(Constants.R_HEADS).stream(),
            repo.getRefDatabase().getRefsByPrefix(Constants.R_TAGS).stream())
        .map(Ref::getObjectId)
        .filter(Objects::nonNull)
        .collect(toSet());
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
      if (obj instanceof RevTag) {
        obj = rw.peel(obj);
      }
      if (obj instanceof RevCommit) {
        out.add((RevCommit) obj);
      }
    }
  }

  private final ProjectCache projectCache;
  private final MergeUtil.Factory mergeUtilFactory;
  private final Provider<InternalChangeQuery> queryProvider;

  @Inject
  SubmitDryRun(
      ProjectCache projectCache,
      MergeUtil.Factory mergeUtilFactory,
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
        logger.atSevere().log(errorMsg);
        throw new IntegrationException(errorMsg);
    }
  }

  private ProjectState getProject(Branch.NameKey branch) throws NoSuchProjectException {
    ProjectState p = projectCache.get(branch.project());
    if (p == null) {
      throw new NoSuchProjectException(branch.project());
    }
    return p;
  }
}
