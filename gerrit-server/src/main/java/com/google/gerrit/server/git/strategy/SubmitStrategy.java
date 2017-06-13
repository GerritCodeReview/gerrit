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

import com.google.common.collect.ListMultimap;
import com.google.common.collect.Sets;
import com.google.gerrit.extensions.api.changes.RecipientType;
import com.google.gerrit.extensions.api.changes.SubmitInput;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.change.RebaseChangeOp;
import com.google.gerrit.server.change.Submit.TestSubmitInput;
import com.google.gerrit.server.extensions.events.ChangeMerged;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.CodeReviewCommit.CodeReviewRevWalk;
import com.google.gerrit.server.git.EmailMerge;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.IntegrationException;
import com.google.gerrit.server.git.LabelNormalizer;
import com.google.gerrit.server.git.MergeOp.CommitStatus;
import com.google.gerrit.server.git.MergeSorter;
import com.google.gerrit.server.git.MergeTip;
import com.google.gerrit.server.git.MergeUtil;
import com.google.gerrit.server.git.RebaseSorter;
import com.google.gerrit.server.git.SubmoduleOp;
import com.google.gerrit.server.git.TagCache;
import com.google.gerrit.server.git.validators.OnSubmitValidators;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.util.RequestId;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.assistedinject.Assisted;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevFlag;

/**
 * Base class that submit strategies must extend.
 *
 * <p>A submit strategy for a certain {@link SubmitType} defines how the submitted commits should be
 * merged.
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
          SubmitType submitType,
          Branch.NameKey destBranch,
          CommitStatus commitStatus,
          CodeReviewRevWalk rw,
          IdentifiedUser caller,
          MergeTip mergeTip,
          RevFlag canMergeFlag,
          ReviewDb db,
          Set<RevCommit> alreadyAccepted,
          Set<CodeReviewCommit> incoming,
          RequestId submissionId,
          SubmitInput submitInput,
          ListMultimap<RecipientType, Account.Id> accountsToNotify,
          SubmoduleOp submoduleOp,
          boolean dryrun);
    }

    final AccountCache accountCache;
    final ApprovalsUtil approvalsUtil;
    final ChangeControl.GenericFactory changeControlFactory;
    final ChangeMerged changeMerged;
    final ChangeMessagesUtil cmUtil;
    final EmailMerge.Factory mergedSenderFactory;
    final GitRepositoryManager repoManager;
    final LabelNormalizer labelNormalizer;
    final PatchSetInfoFactory patchSetInfoFactory;
    final PatchSetUtil psUtil;
    final ProjectCache projectCache;
    final PersonIdent serverIdent;
    final RebaseChangeOp.Factory rebaseFactory;
    final OnSubmitValidators.Factory onSubmitValidatorsFactory;
    final TagCache tagCache;
    final InternalChangeQuery internalChangeQuery;

    final Branch.NameKey destBranch;
    final CodeReviewRevWalk rw;
    final CommitStatus commitStatus;
    final IdentifiedUser caller;
    final MergeTip mergeTip;
    final RevFlag canMergeFlag;
    final ReviewDb db;
    final Set<RevCommit> alreadyAccepted;
    final RequestId submissionId;
    final SubmitType submitType;
    final SubmitInput submitInput;
    final ListMultimap<RecipientType, Account.Id> accountsToNotify;
    final SubmoduleOp submoduleOp;

    final ProjectState project;
    final MergeSorter mergeSorter;
    final RebaseSorter rebaseSorter;
    final MergeUtil mergeUtil;
    final boolean dryrun;

    @Inject
    Arguments(
        AccountCache accountCache,
        ApprovalsUtil approvalsUtil,
        ChangeControl.GenericFactory changeControlFactory,
        ChangeMerged changeMerged,
        ChangeMessagesUtil cmUtil,
        EmailMerge.Factory mergedSenderFactory,
        GitRepositoryManager repoManager,
        LabelNormalizer labelNormalizer,
        MergeUtil.Factory mergeUtilFactory,
        PatchSetInfoFactory patchSetInfoFactory,
        PatchSetUtil psUtil,
        @GerritPersonIdent PersonIdent serverIdent,
        ProjectCache projectCache,
        RebaseChangeOp.Factory rebaseFactory,
        OnSubmitValidators.Factory onSubmitValidatorsFactory,
        TagCache tagCache,
        InternalChangeQuery internalChangeQuery,
        @Assisted Branch.NameKey destBranch,
        @Assisted CommitStatus commitStatus,
        @Assisted CodeReviewRevWalk rw,
        @Assisted IdentifiedUser caller,
        @Assisted MergeTip mergeTip,
        @Assisted RevFlag canMergeFlag,
        @Assisted ReviewDb db,
        @Assisted Set<RevCommit> alreadyAccepted,
        @Assisted Set<CodeReviewCommit> incoming,
        @Assisted RequestId submissionId,
        @Assisted SubmitType submitType,
        @Assisted SubmitInput submitInput,
        @Assisted ListMultimap<RecipientType, Account.Id> accountsToNotify,
        @Assisted SubmoduleOp submoduleOp,
        @Assisted boolean dryrun) {
      this.accountCache = accountCache;
      this.approvalsUtil = approvalsUtil;
      this.changeControlFactory = changeControlFactory;
      this.changeMerged = changeMerged;
      this.mergedSenderFactory = mergedSenderFactory;
      this.repoManager = repoManager;
      this.cmUtil = cmUtil;
      this.labelNormalizer = labelNormalizer;
      this.patchSetInfoFactory = patchSetInfoFactory;
      this.psUtil = psUtil;
      this.projectCache = projectCache;
      this.rebaseFactory = rebaseFactory;
      this.tagCache = tagCache;
      this.internalChangeQuery = internalChangeQuery;

      this.serverIdent = serverIdent;
      this.destBranch = destBranch;
      this.commitStatus = commitStatus;
      this.rw = rw;
      this.caller = caller;
      this.mergeTip = mergeTip;
      this.canMergeFlag = canMergeFlag;
      this.db = db;
      this.alreadyAccepted = alreadyAccepted;
      this.submissionId = submissionId;
      this.submitType = submitType;
      this.submitInput = submitInput;
      this.accountsToNotify = accountsToNotify;
      this.submoduleOp = submoduleOp;
      this.dryrun = dryrun;

      this.project =
          checkNotNull(
              projectCache.get(destBranch.getParentKey()),
              "project not found: %s",
              destBranch.getParentKey());
      this.mergeSorter = new MergeSorter(rw, alreadyAccepted, canMergeFlag, incoming);
      this.rebaseSorter =
          new RebaseSorter(
              rw,
              mergeTip.getInitialTip(),
              alreadyAccepted,
              canMergeFlag,
              internalChangeQuery,
              incoming);
      this.mergeUtil = mergeUtilFactory.create(project);
      this.onSubmitValidatorsFactory = onSubmitValidatorsFactory;
    }
  }

  final Arguments args;

  SubmitStrategy(Arguments args) {
    this.args = checkNotNull(args);
  }

  /**
   * Add operations to a batch update that execute this submit strategy.
   *
   * <p>Guarantees exactly one op is added to the update for each change in the input set.
   *
   * @param bu batch update to add operations to.
   * @param toMerge the set of submitted commits that should be merged using this submit strategy.
   *     Implementations are responsible for ordering of commits, and will not modify the input in
   *     place.
   * @throws IntegrationException if an error occurred initializing the operations (as opposed to an
   *     error during execution, which will be reported only when the batch update executes the
   *     operations).
   */
  public final void addOps(BatchUpdate bu, Set<CodeReviewCommit> toMerge)
      throws IntegrationException {
    List<SubmitStrategyOp> ops = buildOps(toMerge);
    Set<CodeReviewCommit> added = Sets.newHashSetWithExpectedSize(ops.size());

    for (SubmitStrategyOp op : ops) {
      added.add(op.getCommit());
    }

    // First add ops for any implicitly merged changes.
    List<CodeReviewCommit> difference = new ArrayList<>(Sets.difference(toMerge, added));
    Collections.reverse(difference);
    for (CodeReviewCommit c : difference) {
      Change.Id id = c.change().getId();
      bu.addOp(c.change().getId(), new ImplicitIntegrateOp(args, c));
      maybeAddTestHelperOp(bu, id);
    }

    // Then ops for explicitly merged changes
    for (SubmitStrategyOp op : ops) {
      bu.addOp(op.getId(), op);
      maybeAddTestHelperOp(bu, op.getId());
    }
  }

  private void maybeAddTestHelperOp(BatchUpdate bu, Change.Id changeId) {
    if (args.submitInput instanceof TestSubmitInput) {
      bu.addOp(changeId, new TestHelperOp(changeId, args));
    }
  }

  protected abstract List<SubmitStrategyOp> buildOps(Collection<CodeReviewCommit> toMerge)
      throws IntegrationException;
}
