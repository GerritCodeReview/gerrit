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

package com.google.gerrit.server.submit;

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.gerrit.server.project.ProjectCache.illegalState;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.gerrit.entities.BooleanProjectConfig;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.SubmissionId;
import com.google.gerrit.extensions.api.changes.SubmitInput;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.approval.ApprovalsUtil;
import com.google.gerrit.server.change.LabelNormalizer;
import com.google.gerrit.server.change.RebaseChangeOp;
import com.google.gerrit.server.change.SetPrivateOp;
import com.google.gerrit.server.change.TestSubmitInput;
import com.google.gerrit.server.extensions.events.ChangeMerged;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.CodeReviewCommit.CodeReviewRevWalk;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MergeTip;
import com.google.gerrit.server.git.MergeUtil;
import com.google.gerrit.server.git.MergeUtilFactory;
import com.google.gerrit.server.git.TagCache;
import com.google.gerrit.server.git.validators.OnSubmitValidators;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.patch.SubmitWithStickyApprovalDiff;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.submit.MergeOp.CommitStatus;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
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
        factory(EmailMerge.Factory.class);
      }
    };
  }

  static class Arguments {
    interface Factory {
      Arguments create(
          SubmitType submitType,
          BranchNameKey destBranch,
          CommitStatus commitStatus,
          CodeReviewRevWalk rw,
          IdentifiedUser caller,
          MergeTip mergeTip,
          RevFlag canMergeFlag,
          Set<RevCommit> alreadyAccepted,
          Set<CodeReviewCommit> incoming,
          SubmissionId submissionId,
          SubmitInput submitInput,
          SubmoduleCommits submoduleCommits,
          SubscriptionGraph subscriptionGraph,
          boolean dryrun);
    }

    final AccountCache accountCache;
    final ApprovalsUtil approvalsUtil;
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
    final Provider<InternalChangeQuery> queryProvider;
    final ProjectConfig.Factory projectConfigFactory;
    final SetPrivateOp.Factory setPrivateOpFactory;
    final SubmitWithStickyApprovalDiff submitWithStickyApprovalDiff;

    final BranchNameKey destBranch;
    final CodeReviewRevWalk rw;
    final CommitStatus commitStatus;
    final IdentifiedUser caller;
    final MergeTip mergeTip;
    final RevFlag canMergeFlag;
    final Set<RevCommit> alreadyAccepted;
    final SubmissionId submissionId;
    final SubmitType submitType;
    final SubmitInput submitInput;
    final SubscriptionGraph subscriptionGraph;
    final SubmoduleCommits submoduleCommits;

    final ProjectState project;
    final MergeSorter mergeSorter;
    final RebaseSorter rebaseSorter;
    final MergeUtil mergeUtil;
    final boolean dryrun;

    @Inject
    Arguments(
        AccountCache accountCache,
        ApprovalsUtil approvalsUtil,
        ChangeMerged changeMerged,
        ChangeMessagesUtil cmUtil,
        EmailMerge.Factory mergedSenderFactory,
        GitRepositoryManager repoManager,
        LabelNormalizer labelNormalizer,
        MergeUtilFactory mergeUtilFactory,
        PatchSetInfoFactory patchSetInfoFactory,
        PatchSetUtil psUtil,
        @GerritPersonIdent PersonIdent serverIdent,
        ProjectCache projectCache,
        RebaseChangeOp.Factory rebaseFactory,
        OnSubmitValidators.Factory onSubmitValidatorsFactory,
        TagCache tagCache,
        Provider<InternalChangeQuery> queryProvider,
        ProjectConfig.Factory projectConfigFactory,
        SetPrivateOp.Factory setPrivateOpFactory,
        SubmitWithStickyApprovalDiff submitWithStickyApprovalDiff,
        @Assisted BranchNameKey destBranch,
        @Assisted CommitStatus commitStatus,
        @Assisted CodeReviewRevWalk rw,
        @Assisted IdentifiedUser caller,
        @Assisted MergeTip mergeTip,
        @Assisted RevFlag canMergeFlag,
        @Assisted Set<RevCommit> alreadyAccepted,
        @Assisted Set<CodeReviewCommit> incoming,
        @Assisted SubmissionId submissionId,
        @Assisted SubmitType submitType,
        @Assisted SubmitInput submitInput,
        @Assisted SubscriptionGraph subscriptionGraph,
        @Assisted SubmoduleCommits submoduleCommits,
        @Assisted boolean dryrun) {
      this.accountCache = accountCache;
      this.approvalsUtil = approvalsUtil;
      this.changeMerged = changeMerged;
      this.mergedSenderFactory = mergedSenderFactory;
      this.repoManager = repoManager;
      this.cmUtil = cmUtil;
      this.labelNormalizer = labelNormalizer;
      this.projectConfigFactory = projectConfigFactory;
      this.patchSetInfoFactory = patchSetInfoFactory;
      this.psUtil = psUtil;
      this.projectCache = projectCache;
      this.rebaseFactory = rebaseFactory;
      this.tagCache = tagCache;
      this.queryProvider = queryProvider;
      this.setPrivateOpFactory = setPrivateOpFactory;
      this.submitWithStickyApprovalDiff = submitWithStickyApprovalDiff;

      this.serverIdent = serverIdent;
      this.destBranch = destBranch;
      this.commitStatus = commitStatus;
      this.rw = rw;
      this.caller = caller;
      this.mergeTip = mergeTip;
      this.canMergeFlag = canMergeFlag;
      this.alreadyAccepted = alreadyAccepted;
      this.submissionId = submissionId;
      this.submitType = submitType;
      this.submitInput = submitInput;
      this.submoduleCommits = submoduleCommits;
      this.subscriptionGraph = subscriptionGraph;
      this.dryrun = dryrun;

      this.project =
          projectCache.get(destBranch.project()).orElseThrow(illegalState(destBranch.project()));
      this.mergeSorter =
          new MergeSorter(caller, rw, alreadyAccepted, canMergeFlag, queryProvider, incoming);
      Set<RevCommit> uninterestingBranchTips;
      if (project.is(BooleanProjectConfig.CREATE_NEW_CHANGE_FOR_ALL_NOT_IN_TARGET)) {
        RevCommit initialTip = mergeTip.getInitialTip();
        uninterestingBranchTips = initialTip == null ? Set.of() : Set.of(initialTip);
      } else {
        uninterestingBranchTips = alreadyAccepted;
      }
      this.rebaseSorter =
          new RebaseSorter(
              caller,
              rw,
              uninterestingBranchTips,
              alreadyAccepted,
              canMergeFlag,
              queryProvider,
              incoming);
      this.mergeUtil = mergeUtilFactory.create(project);
      this.onSubmitValidatorsFactory = onSubmitValidatorsFactory;
    }
  }

  final Arguments args;

  private final Set<SubmitStrategyOp> submitStrategyOps;

  SubmitStrategy(Arguments args) {
    this.args = requireNonNull(args);
    this.submitStrategyOps = new HashSet<>();
  }

  /**
   * Returns the updated changed after this submit strategy has been executed.
   *
   * @return the updated changes after this submit strategy has been executed
   */
  public ImmutableMap<Change.Id, Change> getUpdatedChanges() {
    return submitStrategyOps.stream()
        .map(SubmitStrategyOp::getUpdatedChange)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(toImmutableMap(c -> c.getId(), c -> c));
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
   */
  public final void addOps(BatchUpdate bu, Set<CodeReviewCommit> toMerge) {
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
      bu.addOp(id, args.setPrivateOpFactory.create(false, null));
      ImplicitIntegrateOp implicitIntegrateOp = new ImplicitIntegrateOp(args, c);
      bu.addOp(id, implicitIntegrateOp);
      maybeAddTestHelperOp(bu, id);
      this.submitStrategyOps.add(implicitIntegrateOp);
    }

    // Then ops for explicitly merged changes
    for (SubmitStrategyOp op : ops) {
      bu.addOp(op.getId(), args.setPrivateOpFactory.create(false, null));
      bu.addOp(op.getId(), op);
      maybeAddTestHelperOp(bu, op.getId());
      this.submitStrategyOps.add(op);
    }
  }

  private void maybeAddTestHelperOp(BatchUpdate bu, Change.Id changeId) {
    if (args.submitInput instanceof TestSubmitInput) {
      bu.addOp(changeId, new TestHelperOp(changeId, args));
    }
  }

  protected abstract ImmutableList<SubmitStrategyOp> buildOps(Collection<CodeReviewCommit> toMerge);
}
