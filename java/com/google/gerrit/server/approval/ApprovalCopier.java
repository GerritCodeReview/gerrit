// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.approval;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.gerrit.server.project.ProjectCache.illegalState;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Table;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.LabelTypes;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.entities.Project;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.client.ChangeKind;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.change.ChangeKindCache;
import com.google.gerrit.server.change.LabelNormalizer;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.logging.Metadata;
import com.google.gerrit.server.logging.TraceContext;
import com.google.gerrit.server.logging.TraceContext.TraceTimer;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.approval.ApprovalContext;
import com.google.gerrit.server.query.approval.ApprovalQueryBuilder;
import com.google.gerrit.server.util.LabelVote;
import com.google.gerrit.server.util.ManualRequestContext;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Computes copied approvals for a given patch set.
 *
 * <p>Approvals are copied if:
 *
 * <ul>
 *   <li>the approval on the previous patch set matches the copy condition of its label
 *   <li>the approval is not overridden by a current approval on the patch set
 * </ul>
 *
 * <p>Callers should store the copied approvals in NoteDb when a new patch set is created.
 */
@Singleton
@VisibleForTesting
public class ApprovalCopier {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @AutoValue
  public abstract static class Result {
    /**
     * Approvals that have been copied from the previous patch set.
     *
     * <p>An approval is copied if:
     *
     * <ul>
     *   <li>the approval on the previous patch set matches the copy condition of its label
     *   <li>the approval is not overridden by a current approval on the patch set
     * </ul>
     */
    public abstract ImmutableSet<PatchSetApprovalData> copiedApprovals();

    /**
     * Approvals on the previous patch set that have not been copied to the patch set.
     *
     * <p>These approvals didn't match the copy condition of their labels and hence haven't been
     * copied.
     *
     * <p>Only returns non-copied approvals of the previous patch set. Approvals from earlier patch
     * sets that were outdated before are not included.
     */
    public abstract ImmutableSet<PatchSetApprovalData> outdatedApprovals();

    static Result empty() {
      return create(
          /* copiedApprovals= */ ImmutableSet.of(), /* outdatedApprovals= */ ImmutableSet.of());
    }

    @VisibleForTesting
    public static Result create(
        ImmutableSet<PatchSetApprovalData> copiedApprovals,
        ImmutableSet<PatchSetApprovalData> outdatedApprovals) {
      return new AutoValue_ApprovalCopier_Result(copiedApprovals, outdatedApprovals);
    }

    /**
     * A {@link PatchSetApproval} with information about which atoms of the copy condition are
     * passing/failing.
     */
    @AutoValue
    public abstract static class PatchSetApprovalData {
      /** The approval. */
      public abstract PatchSetApproval patchSetApproval();

      /**
       * Lists the leaf predicates of the copy condition that are fulfilled.
       *
       * <p>Example: The expression
       *
       * <pre>
       * changekind:TRIVIAL_REBASE OR is:MIN
       * </pre>
       *
       * has two leaf predicates:
       *
       * <ul>
       *   <li>changekind:TRIVIAL_REBASE
       *   <li>is:MIN
       * </ul>
       *
       * This method will return the leaf predicates that are fulfilled, for example if only the
       * first predicate is fulfilled, the returned list will be equal to
       * ["changekind:TRIVIAL_REBASE"].
       *
       * <p>Empty if the label type is missing, if there is no copy condition or if the copy
       * condition is not parseable.
       */
      public abstract ImmutableSet<String> passingAtoms();

      /**
       * Lists the leaf predicates of the copy condition that are not fulfilled. See {@link
       * #passingAtoms()} for more details.
       *
       * <p>Empty if the label type is missing, if there is no copy condition or if the copy
       * condition is not parseable.
       */
      public abstract ImmutableSet<String> failingAtoms();

      @VisibleForTesting
      public static PatchSetApprovalData create(
          PatchSetApproval approval,
          ImmutableSet<String> passingAtoms,
          ImmutableSet<String> failingAtoms) {
        return new AutoValue_ApprovalCopier_Result_PatchSetApprovalData(
            approval, passingAtoms, failingAtoms);
      }

      private static PatchSetApprovalData createForMissingLabelType(PatchSetApproval approval) {
        return new AutoValue_ApprovalCopier_Result_PatchSetApprovalData(
            approval, ImmutableSet.of(), ImmutableSet.of());
      }
    }
  }

  private final GitRepositoryManager repoManager;
  private final ProjectCache projectCache;
  private final ChangeKindCache changeKindCache;
  private final PatchSetUtil psUtil;
  private final LabelNormalizer labelNormalizer;
  private final ApprovalQueryBuilder approvalQueryBuilder;
  private final OneOffRequestContext requestContext;

  @Inject
  ApprovalCopier(
      GitRepositoryManager repoManager,
      ProjectCache projectCache,
      ChangeKindCache changeKindCache,
      PatchSetUtil psUtil,
      LabelNormalizer labelNormalizer,
      ApprovalQueryBuilder approvalQueryBuilder,
      OneOffRequestContext requestContext) {
    this.repoManager = repoManager;
    this.projectCache = projectCache;
    this.changeKindCache = changeKindCache;
    this.psUtil = psUtil;
    this.labelNormalizer = labelNormalizer;
    this.approvalQueryBuilder = approvalQueryBuilder;
    this.requestContext = requestContext;
  }

  /**
   * Returns all copied approvals that apply to the given patch set.
   *
   * <p>Approvals are copied if:
   *
   * <ul>
   *   <li>the approval on the previous patch set matches the copy condition of its label
   *   <li>the approval is not overridden by a current approval on the patch set
   * </ul>
   */
  @VisibleForTesting
  public Result forPatchSet(ChangeNotes notes, PatchSet ps, RevWalk rw, Config repoConfig) {
    ProjectState project;
    try (TraceTimer traceTimer =
        TraceContext.newTimer(
            "Computing labels for patch set",
            Metadata.builder()
                .changeId(notes.load().getChangeId().get())
                .patchSetId(ps.id().get())
                .build())) {
      project =
          projectCache
              .get(notes.getProjectName())
              .orElseThrow(illegalState(notes.getProjectName()));
      return computeForPatchSet(project.getLabelTypes(), notes, ps, rw, repoConfig);
    }
  }

  /**
   * Returns all follow-up patch sets of the given patch set to which the given approval is
   * copyable.
   *
   * <p>An approval is considered as copyable to a follow-up patch set if it matches the copy rules
   * of the label and it is copyable to all intermediate follow-up patch sets as well.
   *
   * <p>The returned follow-up patch sets are returned in the order of their patch set IDs.
   *
   * <p>Note: This method only checks the copy rules to detect if the approval is copyable. There
   * are other factors, not checked here, that can prevent the copying of the approval to the
   * returned follow-up patch sets (e.g. if they already have a matching non-copy approval that
   * prevents the copying).
   *
   * @param changeNotes the change notes
   * @param sourcePatchSet the patch set on which the approval was applied
   * @param approverId the account ID of the user that applied the approval
   * @param label the label of the approval that was applied
   * @param approvalValue the value of the approval that was applied
   * @return the follow-up patch sets to which the approval is copyable, ordered by patch set ID
   */
  public ImmutableList<PatchSet.Id> forApproval(
      ChangeNotes changeNotes,
      PatchSet sourcePatchSet,
      Account.Id approverId,
      String label,
      short approvalValue)
      throws IOException {
    ImmutableList.Builder<PatchSet.Id> targetPatchSetsBuilder = ImmutableList.builder();

    Optional<LabelType> labelType =
        projectCache
            .get(changeNotes.getProjectName())
            .orElseThrow(illegalState(changeNotes.getProjectName()))
            .getLabelTypes()
            .byLabel(label);
    if (!labelType.isPresent()) {
      // no label type exists for this label, hence this approval cannot be copied
      return ImmutableList.of();
    }

    try (Repository repo = repoManager.openRepository(changeNotes.getProjectName());
        RevWalk revWalk = new RevWalk(repo)) {
      ImmutableList<PatchSet.Id> followUpPatchSets =
          changeNotes.getPatchSets().keySet().stream()
              .filter(psId -> psId.get() > sourcePatchSet.id().get())
              .collect(toImmutableList());
      PatchSet priorPatchSet = sourcePatchSet;

      // Iterate over the follow-up patch sets in order to copy the approval from their prior patch
      // set if possible (copy from PS N-1 to PS N).
      for (PatchSet.Id followUpPatchSetId : followUpPatchSets) {
        PatchSet followUpPatchSet = psUtil.get(changeNotes, followUpPatchSetId);
        ChangeKind changeKind =
            changeKindCache.getChangeKind(
                changeNotes.getProjectName(),
                revWalk,
                repo.getConfig(),
                priorPatchSet.commitId(),
                followUpPatchSet.commitId());
        boolean isMerge = isMerge(changeNotes.getProjectName(), revWalk, followUpPatchSet);

        if (computeCopyResult(
                changeNotes,
                priorPatchSet.id(),
                followUpPatchSet,
                approverId,
                labelType.get(),
                approvalValue,
                changeKind,
                isMerge,
                revWalk,
                repo.getConfig())
            .canCopy()) {
          targetPatchSetsBuilder.add(followUpPatchSetId);
        } else {
          // The approval is not copyable to this follow-up patch set.
          // This means it's also not copyable to any further follow-up patch set and we should stop
          // the loop here.
          break;
        }
        priorPatchSet = followUpPatchSet;
      }
    }
    return targetPatchSetsBuilder.build();
  }

  /**
   * Checks whether a given approval can be copied from the given source patch set to the given
   * target patch set.
   *
   * <p>The returned result also informs about which atoms of the copy condition are
   * passing/failing.
   */
  private ApprovalCopyResult computeCopyResult(
      ChangeNotes changeNotes,
      PatchSet.Id sourcePatchSetId,
      PatchSet targetPatchSet,
      Account.Id approverId,
      LabelType labelType,
      short approvalValue,
      ChangeKind changeKind,
      boolean isMerge,
      RevWalk revWalk,
      Config repoConfig) {
    if (!labelType.getCopyCondition().isPresent()) {
      return ApprovalCopyResult.createForMissingCopyCondition();
    }
    ApprovalContext ctx =
        ApprovalContext.create(
            changeNotes,
            sourcePatchSetId,
            approverId,
            labelType,
            approvalValue,
            targetPatchSet,
            changeKind,
            isMerge,
            revWalk,
            repoConfig);
    try {
      // Use a request context to run checks as an internal user with expanded visibility. This is
      // so that the output of the copy condition does not depend on who is running the current
      // request (e.g. a group used in this query might not be visible to the person sending this
      // request).
      try (ManualRequestContext ignored = requestContext.open()) {
        Predicate<ApprovalContext> copyConditionPredicate =
            approvalQueryBuilder.parse(labelType.getCopyCondition().get());
        boolean canCopy = copyConditionPredicate.asMatchable().match(ctx);
        ImmutableSet.Builder<String> passingAtomsBuilder = ImmutableSet.builder();
        ImmutableSet.Builder<String> failingAtomsBuilder = ImmutableSet.builder();
        evaluateAtoms(copyConditionPredicate, ctx, passingAtomsBuilder, failingAtomsBuilder);
        ImmutableSet<String> passingAtoms = passingAtomsBuilder.build();
        ImmutableSet<String> failingAtoms = failingAtomsBuilder.build();
        logger.atFine().log(
            "%s copy %s of account %d on change %d from patch set %d to patch set %d"
                + " (copyCondition = %s, passingAtoms = %s, failingAtoms = %s, changeKind = %s)",
            canCopy ? "Can" : "Cannot",
            LabelVote.create(labelType.getName(), approvalValue).format(),
            approverId.get(),
            changeNotes.getChangeId().get(),
            sourcePatchSetId.get(),
            targetPatchSet.id().get(),
            labelType.getCopyCondition().get(),
            passingAtoms,
            failingAtoms,
            changeKind.name());
        return ApprovalCopyResult.create(canCopy, passingAtoms, failingAtoms);
      }
    } catch (QueryParseException e) {
      logger.atWarning().withCause(e).log(
          "Unable to copy label because config is invalid. This should have been caught before.");
      return ApprovalCopyResult.createForNonParseableCopyCondition();
    }
  }

  private Result computeForPatchSet(
      LabelTypes labelTypes,
      ChangeNotes notes,
      PatchSet targetPatchSet,
      RevWalk rw,
      Config repoConfig) {
    Project.NameKey projectName = notes.getProjectName();
    PatchSet.Id targetPsId = targetPatchSet.id();

    // Bail out immediately if this is the first patch set. Return only approvals granted on the
    // given patch set.
    if (targetPsId.get() == 1) {
      return Result.empty();
    }
    Map.Entry<PatchSet.Id, PatchSet> priorPatchSet =
        notes.load().getPatchSets().lowerEntry(targetPsId);
    if (priorPatchSet == null) {
      return Result.empty();
    }

    Table<String, Account.Id, PatchSetApproval> currentApprovalsByUser = HashBasedTable.create();
    ImmutableList<PatchSetApproval> nonCopiedApprovalsForGivenPatchSet =
        notes.load().getApprovals().onlyNonCopied().get(targetPatchSet.id());
    nonCopiedApprovalsForGivenPatchSet.forEach(
        psa -> currentApprovalsByUser.put(psa.label(), psa.accountId(), psa));

    Table<String, Account.Id, Result.PatchSetApprovalData> copiedApprovalsByUser =
        HashBasedTable.create();
    ImmutableSet.Builder<Result.PatchSetApprovalData> outdatedApprovalsBuilder =
        ImmutableSet.builder();

    ImmutableList<PatchSetApproval> priorApprovals =
        notes.load().getApprovals().all().get(priorPatchSet.getKey());

    // Add labels from the previous patch set to the result in case the label isn't already there
    // and settings as well as change kind allow copying.
    ChangeKind changeKind =
        changeKindCache.getChangeKind(
            projectName,
            rw,
            repoConfig,
            priorPatchSet.getValue().commitId(),
            targetPatchSet.commitId());
    boolean isMerge = isMerge(projectName, rw, targetPatchSet);
    logger.atFine().log(
        "change kind for patch set %d of change %d against prior patch set %s is %s",
        targetPatchSet.id().get(),
        targetPatchSet.id().changeId().get(),
        priorPatchSet.getValue().id().changeId(),
        changeKind);

    for (PatchSetApproval priorPsa : priorApprovals) {
      if (priorPsa.value() == 0) {
        // approvals with a zero vote record the deletion of a vote,
        // they should neither be copied nor be reported as outdated, hence just skip them
        continue;
      }

      Optional<LabelType> labelType = labelTypes.byLabel(priorPsa.labelId());
      if (!labelType.isPresent()) {
        logger.atFine().log(
            "approval %d on label %s of patch set %d of change %d cannot be copied"
                + " to patch set %d because the label no longer exists on project %s",
            priorPsa.value(),
            priorPsa.label(),
            priorPsa.key().patchSetId().get(),
            priorPsa.key().patchSetId().changeId().get(),
            targetPsId.get(),
            projectName);
        outdatedApprovalsBuilder.add(
            Result.PatchSetApprovalData.createForMissingLabelType(priorPsa));
        continue;
      }
      ApprovalCopyResult approvalCopyResult =
          computeCopyResult(
              notes,
              priorPsa.patchSetId(),
              targetPatchSet,
              priorPsa.accountId(),
              labelType.get(),
              priorPsa.value(),
              changeKind,
              isMerge,
              rw,
              repoConfig);
      if (approvalCopyResult.canCopy()) {
        if (!currentApprovalsByUser.contains(priorPsa.label(), priorPsa.accountId())) {
          PatchSetApproval copiedApproval = priorPsa.copyWithPatchSet(targetPatchSet.id());

          // Normalize the copied approval.
          Optional<PatchSetApproval> copiedApprovalNormalized =
              labelNormalizer.normalize(notes, copiedApproval);
          logger.atFine().log(
              "Copied approval %s has been normalized to %s",
              copiedApproval,
              copiedApprovalNormalized.map(PatchSetApproval::toString).orElse("n/a"));
          if (!copiedApprovalNormalized.isPresent()) {
            continue;
          }

          copiedApprovalsByUser.put(
              priorPsa.label(),
              priorPsa.accountId(),
              Result.PatchSetApprovalData.create(
                  copiedApprovalNormalized.get(),
                  approvalCopyResult.passingAtoms(),
                  approvalCopyResult.failingAtoms()));
        }
      } else {
        outdatedApprovalsBuilder.add(
            Result.PatchSetApprovalData.create(
                priorPsa, approvalCopyResult.passingAtoms(), approvalCopyResult.failingAtoms()));
        continue;
      }
    }

    return Result.create(
        ImmutableSet.copyOf(copiedApprovalsByUser.values()), outdatedApprovalsBuilder.build());
  }

  private boolean isMerge(Project.NameKey project, RevWalk rw, PatchSet patchSet) {
    try {
      return rw.parseCommit(patchSet.commitId()).getParentCount() > 1;
    } catch (IOException e) {
      throw new StorageException(
          String.format(
              "failed to check if patch set %d of change %s in project %s is a merge commit",
              patchSet.id().get(), patchSet.id().changeId(), project),
          e);
    }
  }

  /**
   * Evaluates a predicate of the copy condition and adds its passing and failing atoms to the given
   * builders.
   *
   * @param predicate a predicate of the copy condition that should be evaluated
   * @param approvalContext the approval context against which the predicate should be evaluated
   * @param passingAtoms a builder to which passing atoms should be added
   * @param failingAtoms a builder to which failing atoms should be added
   */
  private static void evaluateAtoms(
      Predicate<ApprovalContext> predicate,
      ApprovalContext approvalContext,
      ImmutableSet.Builder<String> passingAtoms,
      ImmutableSet.Builder<String> failingAtoms) {
    if (predicate.isLeaf()) {
      boolean isPassing = predicate.asMatchable().match(approvalContext);
      (isPassing ? passingAtoms : failingAtoms).add(predicate.getPredicateString());
      return;
    }
    predicate
        .getChildren()
        .forEach(
            childPredicate ->
                evaluateAtoms(childPredicate, approvalContext, passingAtoms, failingAtoms));
  }

  /** Result for checking if an approval can be copied to the next patch set. */
  @AutoValue
  abstract static class ApprovalCopyResult {
    /** Whether the approval can be copied to the next patch set. */
    abstract boolean canCopy();

    /**
     * Lists the leaf predicates of the copy condition that are fulfilled. See {@link
     * Result.PatchSetApprovalData#passingAtoms()} for more details.
     *
     * <p>Empty if there is no copy condition or if the copy condition is not parseable.
     */
    abstract ImmutableSet<String> passingAtoms();

    /**
     * Lists the leaf predicates of the copy condition that are not fulfilled. See {@link
     * Result.PatchSetApprovalData#passingAtoms()} for more details.
     *
     * <p>Empty if there is no copy condition or if the copy condition is not parseable.
     */
    abstract ImmutableSet<String> failingAtoms();

    private static ApprovalCopyResult create(
        boolean canCopy, ImmutableSet<String> passingAtoms, ImmutableSet<String> failingAtoms) {
      return new AutoValue_ApprovalCopier_ApprovalCopyResult(canCopy, passingAtoms, failingAtoms);
    }

    private static ApprovalCopyResult createForMissingCopyCondition() {
      return new AutoValue_ApprovalCopier_ApprovalCopyResult(
          /* canCopy= */ false,
          /* passingAtoms= */ ImmutableSet.of(),
          /* failingAtoms= */ ImmutableSet.of());
    }

    private static ApprovalCopyResult createForNonParseableCopyCondition() {
      return new AutoValue_ApprovalCopier_ApprovalCopyResult(
          /* canCopy= */ false,
          /* passingAtoms= */ ImmutableSet.of(),
          /* failingAtoms= */ ImmutableSet.of());
    }
  }
}
