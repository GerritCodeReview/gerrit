// Copyright (C) 2022 The Android Open Source Project
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

package com.google.gerrit.server.restapi.change;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.auto.factory.AutoFactory;
import com.google.auto.factory.Provided;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Table.Cell;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.LabelId;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.approval.ApprovalCopier;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Optional;

/**
 * Batch update operation that copy approvals that have been newly applied on outdated patch sets to
 * the follow-up patch sets if they are copyable and no non-copied approvals prevent the copying.
 *
 * <p>Must be invoked after the batch update operation which applied new approvals on outdated patch
 * sets (e.g. after {@link PostReviewOp}.
 */
@AutoFactory
public class PostReviewCopyApprovalsOp implements BatchUpdateOp {
  private final ApprovalCopier approvalCopier;
  private final PatchSetUtil patchSetUtil;
  private final PatchSet.Id patchSetId;

  private ChangeContext ctx;
  private ImmutableList<PatchSet.Id> followUpPatchSets;

  @Inject
  PostReviewCopyApprovalsOp(
      @Provided ApprovalCopier approvalCopier,
      @Provided PatchSetUtil patchSetUtil,
      PatchSet.Id patchSetId) {
    this.approvalCopier = approvalCopier;
    this.patchSetUtil = patchSetUtil;
    this.patchSetId = patchSetId;
  }

  @Override
  public boolean updateChange(ChangeContext ctx) throws IOException {
    if (ctx.getNotes().getCurrentPatchSet().id().equals(patchSetId)) {
      // the updated patch set is the current patch, there a no follow-up patch set to which new
      // approvals could be copied
      return false;
    }

    init(ctx);

    boolean dirty = false;
    ImmutableTable<String, Account.Id, Optional<PatchSetApproval>> newApprovals =
        ctx.getUpdate(patchSetId).getApprovals();
    for (Cell<String, Account.Id, Optional<PatchSetApproval>> cell : newApprovals.cellSet()) {
      String label = cell.getRowKey();
      Account.Id approverId = cell.getColumnKey();
      PatchSetApproval.Key psaKey =
          PatchSetApproval.key(patchSetId, approverId, LabelId.create(label));

      if (isRemoval(cell)) {
        if (removeCopies(psaKey)) {
          dirty = true;
        }
        continue;
      }

      PatchSet patchSet = patchSetUtil.get(ctx.getNotes(), patchSetId);
      PatchSetApproval psaOrig = cell.getValue().get();

      // Target patch sets to which the approval is copyable.
      ImmutableList<PatchSet.Id> targetPatchSets =
          approvalCopier.forApproval(ctx.getNotes(), patchSet, psaKey, psaOrig.value());

      // Iterate over all follow-up patch sets, in patch set order.
      for (PatchSet.Id followUpPatchSetId : followUpPatchSets) {
        if (hasOverrideOf(followUpPatchSetId, psaKey)) {
          // a non-copied approval exists that overrides any copied approval
          // -> do not copy the approval to this patch set nor to any follow-up patch sets
          break;
        }

        if (targetPatchSets.contains(followUpPatchSetId)) {
          // The approval is copyable to the new patch set.

          if (hasCopyOfWithValue(followUpPatchSetId, psaKey, psaOrig.value())) {
            // a copy approval with the exact value already exists
            continue;
          }

          // add/update the copied approval on the target patch set
          PatchSetApproval copiedPatchSetApproval = psaOrig.copyWithPatchSet(followUpPatchSetId);
          ctx.getUpdate(followUpPatchSetId).putCopiedApproval(copiedPatchSetApproval);
          dirty = true;
        } else {
          // The approval is not copyable to the new patch set.

          if (hasCopyOf(followUpPatchSetId, psaKey)) {
            // a copy approval exists and should be removed
            removeCopy(followUpPatchSetId, psaKey);
            dirty = true;
          }
        }
      }
    }

    return dirty;
  }

  private void init(ChangeContext ctx) {
    this.ctx = ctx;

    // compute follow-up patch sets (sorted by patch set ID)
    this.followUpPatchSets =
        ctx.getNotes().getPatchSets().keySet().stream()
            .filter(psId -> psId.get() > patchSetId.get())
            .collect(toImmutableList());
  }

  /**
   * Whether the given cell entry from the approval table represents the removal of an approval.
   *
   * @param cell cell entry from the approval table
   * @return {@code true} if the approval is not set or the approval has {@code 0} as the value,
   *     otherwise {@code false}
   */
  private boolean isRemoval(Cell<String, Account.Id, Optional<PatchSetApproval>> cell) {
    return cell.getValue().isEmpty() || cell.getValue().get().value() == 0;
  }

  /**
   * Removes copies of the given approval from all follow-up patch sets.
   *
   * @param psaKey the key of the patch set approval for which copies should be removed from all
   *     follow-up patch sets
   * @return whether any copy approval has been removed
   */
  private boolean removeCopies(PatchSetApproval.Key psaKey) {
    boolean dirty = false;
    for (PatchSet.Id followUpPatchSet : followUpPatchSets) {
      if (hasCopyOf(followUpPatchSet, psaKey)) {
        removeCopy(followUpPatchSet, psaKey);
      } else {
        // Do not remove copy from this follow-up patch sets and also not from any further follow-up
        // patch sets (if the further follow-up patch sets have copies they are copies of a
        // non-copied approval on this follow-up patch set and hence those should not be removed).
        break;
      }
    }
    return dirty;
  }

  /**
   * Removes the copy approval with the given key from the given patch set.
   *
   * @param patchSet patch set from which the copy approval with the given key should be removed
   * @param psaKey the key of the patch set approval for which copies should be removed from the
   *     given patch set
   */
  private void removeCopy(PatchSet.Id patchSet, PatchSetApproval.Key psaKey) {
    ctx.getUpdate(patchSet)
        .removeCopiedApprovalFor(
            ctx.getIdentifiedUser().getRealUser().isIdentifiedUser()
                ? ctx.getIdentifiedUser().getRealUser().getAccountId()
                : null,
            psaKey.accountId(),
            psaKey.labelId().get());
  }

  /**
   * Whether the given patch set has a copy approval with the given key.
   *
   * @param patchSetId the ID of the patch for which it should be checked whether it has a copy
   *     approval with the given key
   * @param psaKey the key of the patch set approval
   */
  private boolean hasCopyOf(PatchSet.Id patchSetId, PatchSetApproval.Key psaKey) {
    return ctx.getNotes().getApprovals().onlyCopied().get(patchSetId).stream()
        .anyMatch(psa -> areAccountAndLabelTheSame(psa.key(), psaKey));
  }

  /**
   * Whether the given patch set has a copy approval with the given key and value.
   *
   * @param patchSetId the ID of the patch for which it should be checked whether it has a copy
   *     approval with the given key and value
   * @param psaKey the key of the patch set approval
   */
  private boolean hasCopyOfWithValue(
      PatchSet.Id patchSetId, PatchSetApproval.Key psaKey, short value) {
    return ctx.getNotes().getApprovals().onlyCopied().get(patchSetId).stream()
        .anyMatch(psa -> areAccountAndLabelTheSame(psa.key(), psaKey) && psa.value() == value);
  }

  /**
   * Whether the given patch set has a normal approval with the given key that overrides copy
   * approvals with that key.
   *
   * @param patchSetId the ID of the patch for which it should be checked whether it has a normal
   *     approval with the given key that overrides copy approvals with that key
   * @param psaKey the key of the patch set approval
   */
  private boolean hasOverrideOf(PatchSet.Id patchSetId, PatchSetApproval.Key psaKey) {
    return ctx.getNotes().getApprovals().onlyNonCopied().get(patchSetId).stream()
        .anyMatch(psa -> areAccountAndLabelTheSame(psa.key(), psaKey));
  }

  private boolean areAccountAndLabelTheSame(
      PatchSetApproval.Key psaKey1, PatchSetApproval.Key psaKey2) {
    return psaKey1.accountId().equals(psaKey2.accountId())
        && psaKey1.labelId().equals(psaKey2.labelId());
  }
}
