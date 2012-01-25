// Copyright (C) 2009 The Android Open Source Project
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

import com.google.gerrit.common.data.ApprovalType;
import com.google.gerrit.common.data.ApprovalTypes;
import com.google.gerrit.reviewdb.client.ApprovalCategory;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gwtorm.server.OrmException;

import java.util.Collections;
import java.util.List;

public class ApprovalsUtil {
  /* Resync the changeOpen status which is cached in the approvals table for
     performance reasons*/
  public static void syncChangeStatus(final ReviewDb db, final Change change)
      throws OrmException {
    final List<PatchSetApproval> approvals =
        db.patchSetApprovals().byChange(change.getId()).toList();
    for (PatchSetApproval a : approvals) {
      a.cache(change);
    }
    db.patchSetApprovals().update(approvals);
  }

  /**
   * Moves the PatchSetApprovals to the last PatchSet on the change.
   *
   * @param db The review database
   * @param change Change to update
   * @param source Original PatchSet to move votes from
   * @param approvalTypes The approval types
   * @throws OrmException
   */
  public static void copyVetoApprovalsToLatestPatchSet(final ReviewDb db, Change change,
      PatchSet.Id source, ApprovalTypes approvalTypes) throws OrmException {
    PatchSet.Id dest = change.currPatchSetId();
    for (PatchSetApproval a : db.patchSetApprovals().byChange(change.getId())) {
      // ApprovalCategory.SUBMIT is still in db but not relevant in git-store
      if (!ApprovalCategory.SUBMIT.equals(a.getCategoryId())) {
        final ApprovalType type = approvalTypes.byId(a.getCategoryId());
        if (a.getPatchSetId().equals(source)
            && type.getCategory().isCopyMinScore() && type.isMaxNegative(a)) {
          // If there was a negative vote on the prior patch set, carry it
          // into this patch set.
          db.patchSetApprovals().insert(
              Collections.singleton(new PatchSetApproval(dest, a)));
        }
      }
    }
  }
}
