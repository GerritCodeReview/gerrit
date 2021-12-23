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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.gerrit.server.project.ProjectCache.illegalState;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Table;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.LabelTypes;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.entities.Project;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.client.ChangeKind;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.server.change.ChangeKindCache;
import com.google.gerrit.server.change.LabelNormalizer;
import com.google.gerrit.server.logging.Metadata;
import com.google.gerrit.server.logging.TraceContext;
import com.google.gerrit.server.logging.TraceContext.TraceTimer;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.patch.DiffNotAvailableException;
import com.google.gerrit.server.patch.DiffOperations;
import com.google.gerrit.server.patch.DiffOptions;
import com.google.gerrit.server.patch.gitdiff.ModifiedFile;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.approval.ApprovalContext;
import com.google.gerrit.server.query.approval.ApprovalQueryBuilder;
import com.google.gerrit.server.query.approval.ListOfFilesUnchangedPredicate;
import com.google.gerrit.server.util.ManualRequestContext;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Computes approvals for a given patch set by looking at approvals applied to the given patch set
 * and by additionally copying approvals from the previous patch set. The latter is done by
 * asserting a change's kind and checking the project config for copy conditions.
 *
 * <p>The result of a copy is stored in NoteDb when a new patch set is created.
 */
@Singleton
class ApprovalCopier {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final DiffOperations diffOperations;
  private final ProjectCache projectCache;
  private final ChangeKindCache changeKindCache;
  private final LabelNormalizer labelNormalizer;
  private final ApprovalQueryBuilder approvalQueryBuilder;
  private final OneOffRequestContext requestContext;
  private final ListOfFilesUnchangedPredicate listOfFilesUnchangedPredicate;

  @Inject
  ApprovalCopier(
      DiffOperations diffOperations,
      ProjectCache projectCache,
      ChangeKindCache changeKindCache,
      LabelNormalizer labelNormalizer,
      ApprovalQueryBuilder approvalQueryBuilder,
      OneOffRequestContext requestContext,
      ListOfFilesUnchangedPredicate listOfFilesUnchangedPredicate) {
    this.diffOperations = diffOperations;
    this.projectCache = projectCache;
    this.changeKindCache = changeKindCache;
    this.labelNormalizer = labelNormalizer;
    this.approvalQueryBuilder = approvalQueryBuilder;
    this.requestContext = requestContext;
    this.listOfFilesUnchangedPredicate = listOfFilesUnchangedPredicate;
  }

  /**
   * Returns all approvals that apply to the given patch set. Honors copied approvals from previous
   * patch-set.
   */
  Iterable<PatchSetApproval> forPatchSet(
      ChangeNotes notes,
      PatchSet ps,
      RevWalk rw,
      Config repoConfig,
      boolean legacyIncludePreviousPatchsets) {
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
      Collection<PatchSetApproval> approvals =
          getForPatchSetWithoutNormalization(
              notes, project, ps, rw, repoConfig, legacyIncludePreviousPatchsets);
      return labelNormalizer.normalize(notes, approvals).getNormalized();
    }
  }

  private boolean canCopyBasedOnBooleanLabelConfigs(
      ProjectState project,
      PatchSetApproval psa,
      PatchSet.Id psId,
      ChangeKind kind,
      boolean isMerge,
      LabelType type,
      @Nullable Map<String, ModifiedFile> baseVsCurrentDiff,
      @Nullable Map<String, ModifiedFile> baseVsPriorDiff,
      @Nullable Map<String, ModifiedFile> priorVsCurrentDiff) {
    int n = psa.key().patchSetId().get();
    checkArgument(n != psId.get());

    if (type.isCopyMinScore() && type.isMaxNegative(psa)) {
      logger.atFine().log(
          "veto approval %s on label %s of patch set %d of change %d can be copied"
              + " to patch set %d because the label has set copyMinScore = true on project %s",
          psa.value(),
          psa.label(),
          n,
          psa.key().patchSetId().changeId().get(),
          psId.get(),
          project.getName());
      return true;
    } else if (type.isCopyMaxScore() && type.isMaxPositive(psa)) {
      logger.atFine().log(
          "max approval %s on label %s of patch set %d of change %d can be copied"
              + " to patch set %d because the label has set copyMaxScore = true on project %s",
          psa.value(),
          psa.label(),
          n,
          psa.key().patchSetId().changeId().get(),
          psId.get(),
          project.getName());
      return true;
    } else if (type.isCopyAnyScore()) {
      logger.atFine().log(
          "approval %d on label %s of patch set %d of change %d can be copied"
              + " to patch set %d because the label has set copyAnyScore = true on project %s",
          psa.value(),
          psa.label(),
          n,
          psa.key().patchSetId().changeId().get(),
          psId.get(),
          project.getName());
      return true;
    } else if (type.getCopyValues().contains(psa.value())) {
      logger.atFine().log(
          "approval %d on label %s of patch set %d of change %d can be copied"
              + " to patch set %d because the label has set copyValue = %d on project %s",
          psa.value(),
          psa.label(),
          n,
          psa.key().patchSetId().changeId().get(),
          psId.get(),
          psa.value(),
          project.getName());
      return true;
    } else if (type.isCopyAllScoresIfListOfFilesDidNotChange()
        && listOfFilesUnchangedPredicate.match(
            baseVsCurrentDiff, baseVsPriorDiff, priorVsCurrentDiff)) {
      logger.atFine().log(
          "approval %d on label %s of patch set %d of change %d can be copied"
              + " to patch set %d because the label has set "
              + "copyAllScoresIfListOfFilesDidNotChange = true on "
              + "project %s and list of files did not change (maybe except a rename, which is "
              + "still the same file).",
          psa.value(),
          psa.label(),
          n,
          psa.key().patchSetId().changeId().get(),
          psId.get(),
          project.getName());
      return true;
    }
    switch (kind) {
      case MERGE_FIRST_PARENT_UPDATE:
        if (type.isCopyAllScoresOnMergeFirstParentUpdate()) {
          logger.atFine().log(
              "approval %d on label %s of patch set %d of change %d can be copied"
                  + " to patch set %d because change kind is %s and the label has set"
                  + " copyAllScoresOnMergeFirstParentUpdate = true on project %s",
              psa.value(),
              psa.label(),
              n,
              psa.key().patchSetId().changeId().get(),
              psId.get(),
              kind,
              project.getName());
          return true;
        }
        return false;
      case NO_CODE_CHANGE:
        if (type.isCopyAllScoresIfNoCodeChange()) {
          logger.atFine().log(
              "approval %d on label %s of patch set %d of change %d can be copied"
                  + " to patch set %d because change kind is %s and the label has set"
                  + " copyAllScoresIfNoCodeChange = true on project %s",
              psa.value(),
              psa.label(),
              n,
              psa.key().patchSetId().changeId().get(),
              psId.get(),
              kind,
              project.getName());
          return true;
        }
        return false;
      case TRIVIAL_REBASE:
        if (type.isCopyAllScoresOnTrivialRebase()) {
          logger.atFine().log(
              "approval %d on label %s of patch set %d of change %d can be copied"
                  + " to patch set %d because change kind is %s and the label has set"
                  + " copyAllScoresOnTrivialRebase = true on project %s",
              psa.value(),
              psa.label(),
              n,
              psa.key().patchSetId().changeId().get(),
              psId.get(),
              kind,
              project.getName());
          return true;
        }
        return false;
      case NO_CHANGE:
        if (type.isCopyAllScoresIfNoChange()) {
          logger.atFine().log(
              "approval %d on label %s of patch set %d of change %d can be copied"
                  + " to patch set %d because change kind is %s and the label has set"
                  + " copyAllScoresIfNoCodeChange = true on project %s",
              psa.value(),
              psa.label(),
              n,
              psa.key().patchSetId().changeId().get(),
              psId.get(),
              kind,
              project.getName());
          return true;
        }
        if (type.isCopyAllScoresOnTrivialRebase()) {
          logger.atFine().log(
              "approval %d on label %s of patch set %d of change %d can be copied"
                  + " to patch set %d because change kind is %s and the label has set"
                  + " copyAllScoresOnTrivialRebase = true on project %s",
              psa.value(),
              psa.label(),
              n,
              psa.key().patchSetId().changeId().get(),
              psId.get(),
              kind,
              project.getName());
          return true;
        }
        if (isMerge && type.isCopyAllScoresOnMergeFirstParentUpdate()) {
          logger.atFine().log(
              "approval %d on label %s of patch set %d of change %d can be copied"
                  + " to patch set %d because change kind is %s and the label has set"
                  + " copyAllScoresOnMergeFirstParentUpdate = true on project %s",
              psa.value(),
              psa.label(),
              n,
              psa.key().patchSetId().changeId().get(),
              psId.get(),
              kind,
              project.getName());
          return true;
        }
        if (type.isCopyAllScoresIfNoCodeChange()) {
          logger.atFine().log(
              "approval %d on label %s of patch set %d of change %d can be copied"
                  + " to patch set %d because change kind is %s and the label has set"
                  + " copyAllScoresIfNoCodeChange = true on project %s",
              psa.value(),
              psa.label(),
              n,
              psa.key().patchSetId().changeId().get(),
              psId.get(),
              kind,
              project.getName());
          return true;
        }
        return false;
      case REWORK:
      default:
        logger.atFine().log(
            "approval %d on label %s of patch set %d of change %d cannot be copied"
                + " to patch set %d because change kind is %s",
            psa.value(), psa.label(), n, psa.key().patchSetId().changeId().get(), psId.get(), kind);
        return false;
    }
  }

  private boolean canCopyBasedOnCopyCondition(
      ChangeNotes changeNotes,
      PatchSetApproval psa,
      PatchSet patchSet,
      LabelType type,
      ChangeKind changeKind,
      boolean isMerge,
      RevWalk revWalk,
      Config repoConfig) {
    if (!type.getCopyCondition().isPresent()) {
      return false;
    }
    ApprovalContext ctx =
        ApprovalContext.create(
            changeNotes, psa, patchSet, changeKind, isMerge, revWalk, repoConfig);
    try {
      // Use a request context to run checks as an internal user with expanded visibility. This is
      // so that the output of the copy condition does not depend on who is running the current
      // request (e.g. a group used in this query might not be visible to the person sending this
      // request).
      try (ManualRequestContext ignored = requestContext.open()) {
        return approvalQueryBuilder.parse(type.getCopyCondition().get()).asMatchable().match(ctx);
      }
    } catch (QueryParseException e) {
      logger.atWarning().withCause(e).log(
          "Unable to copy label because config is invalid. This should have been caught before.");
      return false;
    }
  }

  private Collection<PatchSetApproval> getForPatchSetWithoutNormalization(
      ChangeNotes notes,
      ProjectState project,
      PatchSet patchSet,
      RevWalk rw,
      Config repoConfig,
      boolean legacyIncludePreviousPatchsets) {
    checkState(
        project.getNameKey().equals(notes.getProjectName()),
        "project must match %s, %s",
        project.getNameKey(),
        notes.getProjectName());

    PatchSet.Id psId = patchSet.id();
    // Add approvals on the given patch set to the result
    Table<String, Account.Id, PatchSetApproval> resultByUser = HashBasedTable.create();
    ImmutableList<PatchSetApproval> nonCopiedApprovalsForGivenPatchSet =
        notes.load().getApprovals().get(patchSet.id());
    nonCopiedApprovalsForGivenPatchSet.forEach(
        psa -> resultByUser.put(psa.label(), psa.accountId(), psa));

    // Bail out immediately if this is the first patch set. Return only approvals granted on the
    // given patch set.
    if (psId.get() == 1) {
      return resultByUser.values();
    }
    Map.Entry<PatchSet.Id, PatchSet> priorPatchSet = notes.load().getPatchSets().lowerEntry(psId);
    if (priorPatchSet == null) {
      return resultByUser.values();
    }

    Collection<PatchSetApproval> priorApprovalsIncludingCopied =
        legacyIncludePreviousPatchsets
            ? getForPatchSetWithoutNormalization(
                notes,
                project,
                priorPatchSet.getValue(),
                rw,
                repoConfig,
                legacyIncludePreviousPatchsets)
            : notes.load().getApprovalsWithCopied().get(priorPatchSet.getKey());

    // Add labels from the previous patch set to the result in case the label isn't already there
    // and settings as well as change kind allow copying.
    ChangeKind changeKind =
        changeKindCache.getChangeKind(
            project.getNameKey(),
            rw,
            repoConfig,
            priorPatchSet.getValue().commitId(),
            patchSet.commitId());
    boolean isMerge = isMerge(project.getNameKey(), rw, patchSet);
    logger.atFine().log(
        "change kind for patch set %d of change %d against prior patch set %s is %s",
        patchSet.id().get(),
        patchSet.id().changeId().get(),
        priorPatchSet.getValue().id().changeId(),
        changeKind);

    Map<String, ModifiedFile> baseVsCurrent = null;
    Map<String, ModifiedFile> baseVsPrior = null;
    Map<String, ModifiedFile> priorVsCurrent = null;
    LabelTypes labelTypes = project.getLabelTypes();
    for (PatchSetApproval psa : priorApprovalsIncludingCopied) {
      if (resultByUser.contains(psa.label(), psa.accountId())) {
        continue;
      }
      Optional<LabelType> type = labelTypes.byLabel(psa.labelId());
      // Only compute modified files if there is a relevant label, since this is expensive.
      if (baseVsCurrent == null
          && type.isPresent()
          && type.get().isCopyAllScoresIfListOfFilesDidNotChange()) {
        baseVsCurrent = listModifiedFiles(project, patchSet, rw, repoConfig);
        baseVsPrior = listModifiedFiles(project, priorPatchSet.getValue(), rw, repoConfig);
        priorVsCurrent =
            listModifiedFiles(
                project, priorPatchSet.getValue().commitId(), patchSet.commitId(), rw, repoConfig);
      }
      if (!type.isPresent()) {
        logger.atFine().log(
            "approval %d on label %s of patch set %d of change %d cannot be copied"
                + " to patch set %d because the label no longer exists on project %s",
            psa.value(),
            psa.label(),
            psa.key().patchSetId().get(),
            psa.key().patchSetId().changeId().get(),
            psId.get(),
            project.getName());
        continue;
      }
      if (!canCopyBasedOnBooleanLabelConfigs(
              project,
              psa,
              patchSet.id(),
              changeKind,
              isMerge,
              type.get(),
              baseVsCurrent,
              baseVsPrior,
              priorVsCurrent)
          && !canCopyBasedOnCopyCondition(
              notes, psa, patchSet, type.get(), changeKind, isMerge, rw, repoConfig)) {
        continue;
      }
      resultByUser.put(psa.label(), psa.accountId(), psa.copyWithPatchSet(patchSet.id()));
    }
    return resultByUser.values();
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
   * Gets the modified files between the two latest patch-sets. Can be used to compute difference in
   * files between those two patch-sets .
   */
  private Map<String, ModifiedFile> listModifiedFiles(
      ProjectState project, PatchSet ps, RevWalk revWalk, Config repoConfig) {
    try {
      Integer parentNum =
          listOfFilesUnchangedPredicate.isInitialCommit(project.getNameKey(), ps.commitId())
              ? 0
              : 1;
      return diffOperations.loadModifiedFilesAgainstParent(
          project.getNameKey(),
          ps.commitId(),
          parentNum,
          DiffOptions.DEFAULTS,
          revWalk,
          repoConfig);
    } catch (DiffNotAvailableException ex) {
      throw new StorageException(
          "failed to compute difference in files, so won't copy"
              + " votes on labels even if list of files is the same and "
              + "copyAllIfListOfFilesDidNotChange",
          ex);
    }
  }

  /**
   * Gets the modified files between two commits corresponding to different patchsets of the same
   * change.
   */
  private Map<String, ModifiedFile> listModifiedFiles(
      ProjectState project,
      ObjectId sourceCommit,
      ObjectId targetCommit,
      RevWalk revWalk,
      Config repoConfig) {
    try {
      return diffOperations.loadModifiedFiles(
          project.getNameKey(),
          sourceCommit,
          targetCommit,
          DiffOptions.DEFAULTS,
          revWalk,
          repoConfig);
    } catch (DiffNotAvailableException ex) {
      throw new StorageException(
          "failed to compute difference in files, so won't copy"
              + " votes on labels even if list of files is the same and "
              + "copyAllIfListOfFilesDidNotChange",
          ex);
    }
  }
}
