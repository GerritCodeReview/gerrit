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

package com.google.gerrit.server;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Table;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.client.ChangeKind;
import com.google.gerrit.server.change.ChangeKindCache;
import com.google.gerrit.server.change.LabelNormalizer;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Computes approvals for a given patch set by looking at approvals applied to the given patch set
 * and by additionally inferring approvals from the patch set's parents. The latter is done by
 * asserting a change's kind and checking the project config for allowed forward-inference.
 *
 * <p>The result of a copy may either be stored, as when stamping approvals in the database at
 * submit time, or refreshed on demand, as when reading approvals from the NoteDb.
 */
@Singleton
public class ApprovalInference {
  private final ProjectCache projectCache;
  private final ChangeKindCache changeKindCache;
  private final LabelNormalizer labelNormalizer;
  private final ChangeData.Factory changeDataFactory;
  private final PatchSetUtil psUtil;

  @Inject
  ApprovalInference(
      ProjectCache projectCache,
      ChangeKindCache changeKindCache,
      LabelNormalizer labelNormalizer,
      ChangeData.Factory changeDataFactory,
      PatchSetUtil psUtil) {
    this.projectCache = projectCache;
    this.changeKindCache = changeKindCache;
    this.labelNormalizer = labelNormalizer;
    this.changeDataFactory = changeDataFactory;
    this.psUtil = psUtil;
  }

  /**
   * Returns all approvals that apply to the given patch set. Honors direct and indirect (approval
   * on parents) approvals.
   */
  Iterable<PatchSetApproval> forPatchSet(
      ChangeNotes notes, PatchSet.Id psId, @Nullable RevWalk rw, @Nullable Config repoConfig) {
    Collection<PatchSetApproval> approvals =
        getForPatchSetWithoutNormalization(notes, psId, rw, repoConfig);
    try {
      return labelNormalizer.normalize(notes, approvals).getNormalized();
    } catch (IOException e) {
      throw new StorageException(e);
    }
  }

  private static boolean canCopy(
      ProjectState project, PatchSetApproval psa, PatchSet.Id psId, ChangeKind kind) {
    int n = psa.key().patchSetId().get();
    checkArgument(n != psId.get());
    LabelType type = project.getLabelTypes().byLabel(psa.labelId());
    if (type == null) {
      return false;
    } else if ((type.isCopyMinScore() && type.isMaxNegative(psa))
        || (type.isCopyMaxScore() && type.isMaxPositive(psa))) {
      return true;
    }
    switch (kind) {
      case MERGE_FIRST_PARENT_UPDATE:
        return type.isCopyAllScoresOnMergeFirstParentUpdate();
      case NO_CODE_CHANGE:
        return type.isCopyAllScoresIfNoCodeChange();
      case TRIVIAL_REBASE:
        return type.isCopyAllScoresOnTrivialRebase();
      case NO_CHANGE:
        return type.isCopyAllScoresIfNoChange()
            || type.isCopyAllScoresOnTrivialRebase()
            || type.isCopyAllScoresOnMergeFirstParentUpdate()
            || type.isCopyAllScoresIfNoCodeChange();
      case REWORK:
      default:
        return false;
    }
  }

  private Collection<PatchSetApproval> getForPatchSetWithoutNormalization(
      ChangeNotes notes, PatchSet.Id psId, @Nullable RevWalk rw, @Nullable Config repoConfig) {
    PatchSet ps = psUtil.get(notes, psId);
    if (ps == null) {
      return Collections.emptyList();
    }

    ChangeData cd = changeDataFactory.create(notes);
    ProjectState project;
    try {
      project = projectCache.checkedGet(cd.change().getDest().project());
    } catch (IOException e) {
      throw new StorageException(e);
    }

    // Start by collecting all current approvals
    Table<String, Account.Id, PatchSetApproval> byUser = HashBasedTable.create();
    ListMultimap<PatchSet.Id, PatchSetApproval> all = cd.approvals();
    requireNonNull(all, "all should not be null");
    all.get(ps.id()).forEach(psa -> byUser.put(psa.label(), psa.accountId(), psa));

    // Bail out immediately if this is the first patch set
    if (psId.get() == 1) {
      return byUser.values();
    }

    // Call this algorithm recursively to check if the prior patch set had approvals. This has the
    // advantage that all caches - most importantly ChangeKindCache - have values cached for what we
    // need for this computation.
    // The way this algorithm is written is that any approval will be copied forward by one patch
    // set at a time if configs and change kind allow so. Once an approval is held back - for
    // example because the patch set is a REWORK - it will not be picked up again in a future
    // patch set.
    PatchSet priorPatchSet = notes.load().getPatchSets().lowerEntry(psId).getValue();
    if (priorPatchSet == null) {
      return byUser.values();
    }

    Iterable<PatchSetApproval> priorApprovals =
        getForPatchSetWithoutNormalization(notes, priorPatchSet.id(), rw, repoConfig);
    if (!priorApprovals.iterator().hasNext()) {
      return byUser.values();
    }

    Table<String, Account.Id, PatchSetApproval> wontCopy = HashBasedTable.create();
    ChangeKind kind =
        changeKindCache.getChangeKind(
            project.getNameKey(), rw, repoConfig, priorPatchSet.commitId(), ps.commitId());
    for (PatchSetApproval psa : priorApprovals) {
      if (wontCopy.contains(psa.label(), psa.accountId())) {
        continue;
      }
      if (byUser.contains(psa.label(), psa.accountId())) {
        continue;
      }
      if (!canCopy(project, psa, ps.id(), kind)) {
        wontCopy.put(psa.label(), psa.accountId(), psa);
        continue;
      }
      byUser.put(psa.label(), psa.accountId(), psa.copyWithPatchSet(ps.id()));
    }
    return byUser.values();
  }
}
