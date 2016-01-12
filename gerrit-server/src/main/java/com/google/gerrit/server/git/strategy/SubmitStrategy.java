// Copyright (C) 2012 The Android Open Source Project
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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.change.RebaseChangeOp;
import com.google.gerrit.server.git.BatchUpdate;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.CodeReviewCommit.CodeReviewRevWalk;
import com.google.gerrit.server.git.IntegrationException;
import com.google.gerrit.server.git.MergeOp.CommitStatus;
import com.google.gerrit.server.git.MergeSorter;
import com.google.gerrit.server.git.MergeTip;
import com.google.gerrit.server.git.MergeUtil;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Module;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevFlag;

import java.sql.Timestamp;
import java.util.Collection;
import java.util.Set;

/**
 * Base class that submit strategies must extend.
 * <p>
 * A submit strategy for a certain {@link SubmitType} defines how the submitted
 * commits should be merged.
 */
public abstract class SubmitStrategy {
  public static Module module() {
    return new FactoryModule() {
      @Override
      protected void configure() {
        factory(SubmitStrategy.Arguments.Factory.class);
      }
    };
  }

  static class Arguments {
    interface Factory {
      Arguments create(
          Branch.NameKey destBranch,
          CommitStatus commits,
          CodeReviewRevWalk rw,
          IdentifiedUser caller,
          ObjectInserter inserter,
          Repository repo,
          RevFlag canMergeFlag,
          ReviewDb db,
          Set<RevCommit> alreadyAccepted);
    }

    final ApprovalsUtil approvalsUtil;
    final BatchUpdate.Factory batchUpdateFactory;
    final ChangeControl.GenericFactory changeControlFactory;
    final PatchSetInfoFactory patchSetInfoFactory;
    final ProjectCache projectCache;
    final PersonIdent serverIdent;
    final RebaseChangeOp.Factory rebaseFactory;

    final Branch.NameKey destBranch;
    final CodeReviewRevWalk rw;
    final CommitStatus commits;
    final IdentifiedUser caller;
    final ObjectInserter inserter;
    final Repository repo;
    final RevFlag canMergeFlag;
    final ReviewDb db;
    final Set<RevCommit> alreadyAccepted;

    final ProjectState project;
    final MergeSorter mergeSorter;
    final MergeUtil mergeUtil;

    @AssistedInject
    Arguments(
        ApprovalsUtil approvalsUtil,
        BatchUpdate.Factory batchUpdateFactory,
        ChangeControl.GenericFactory changeControlFactory,
        MergeUtil.Factory mergeUtilFactory,
        PatchSetInfoFactory patchSetInfoFactory,
        @GerritPersonIdent PersonIdent serverIdent,
        ProjectCache projectCache,
        RebaseChangeOp.Factory rebaseFactory,
        @Assisted Branch.NameKey destBranch,
        @Assisted CommitStatus commits,
        @Assisted CodeReviewRevWalk rw,
        @Assisted IdentifiedUser caller,
        @Assisted ObjectInserter inserter,
        @Assisted Repository repo,
        @Assisted RevFlag canMergeFlag,
        @Assisted ReviewDb db,
        @Assisted Set<RevCommit> alreadyAccepted) {
      this.approvalsUtil = approvalsUtil;
      this.batchUpdateFactory = batchUpdateFactory;
      this.changeControlFactory = changeControlFactory;
      this.patchSetInfoFactory = patchSetInfoFactory;
      this.projectCache = projectCache;
      this.rebaseFactory = rebaseFactory;

      this.serverIdent = serverIdent;
      this.destBranch = destBranch;
      this.commits = commits;
      this.rw = rw;
      this.caller = caller;
      this.inserter = inserter;
      this.repo = repo;
      this.canMergeFlag = canMergeFlag;
      this.db = db;
      this.alreadyAccepted = alreadyAccepted;

      this.project = checkNotNull(projectCache.get(destBranch.getParentKey()),
            "project not found: %s", destBranch.getParentKey());
      this.mergeSorter = new MergeSorter(rw, alreadyAccepted, canMergeFlag);
      this.mergeUtil = mergeUtilFactory.create(project);
    }

    BatchUpdate newBatchUpdate(Timestamp when) {
      return batchUpdateFactory
          .create(db, destBranch.getParentKey(), caller, when)
          .setRepository(repo, rw, inserter);
    }
  }

  protected final Arguments args;

  SubmitStrategy(Arguments args) {
    this.args = checkNotNull(args);
  }

  /**
   * Runs this submit strategy.
   * <p>
   * If possible, the provided commits will be merged with this submit strategy.
   *
   * @param currentTip the mergeTip
   * @param toMerge the list of submitted commits that should be merged using
   *        this submit strategy. Implementations are responsible for ordering
   *        of commits, and should not modify the input in place.
   * @return the new merge tip.
   * @throws IntegrationException
   */
  public abstract MergeTip run(CodeReviewCommit currentTip,
      Collection<CodeReviewCommit> toMerge) throws IntegrationException;
}
