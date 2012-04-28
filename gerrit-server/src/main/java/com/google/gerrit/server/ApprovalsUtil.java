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
import com.google.inject.Inject;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class ApprovalsUtil {
  private final ReviewDb db;
  private final ApprovalTypes approvalTypes;

  @Inject
  ApprovalsUtil(ReviewDb db, ApprovalTypes approvalTypes) {
    this.db = db;
    this.approvalTypes = approvalTypes;
  }

  /**
   * Resync the changeOpen status which is cached in the approvals table for
   * performance reasons
   */
  public void syncChangeStatus(final Change change)
      throws OrmException {
    final List<PatchSetApproval> approvals =
        db.patchSetApprovals().byChange(change.getId()).toList();
    for (PatchSetApproval a : approvals) {
      a.cache(change);
    }
    db.patchSetApprovals().update(approvals);
  }

  /**
   * Moves the PatchSetApprovals to the last PatchSet on the change while
   * keeping the vetos.
   *
   * @param change Change to update
   * @throws OrmException
   * @throws IOException
   */
  public void copyVetosToLatestPatchSet(Change change)
      throws OrmException, IOException {
    PatchSet.Id source;
    if (change.getNumberOfPatchSets() > 1) {
      source = new PatchSet.Id(change.getId(), change.getNumberOfPatchSets() - 1);
    } else {
      throw new IOException("Previous patch set could not be found");
    }

    PatchSet.Id dest = change.currPatchSetId();
    for (PatchSetApproval a : db.patchSetApprovals().byPatchSet(source)) {
      // ApprovalCategory.SUBMIT is still in db but not relevant in git-store
      if (!ApprovalCategory.SUBMIT.equals(a.getCategoryId())) {
        final ApprovalType type = approvalTypes.byId(a.getCategoryId());
        if (type.getCategory().isCopyMinScore() && type.isMaxNegative(a)) {
          db.patchSetApprovals().insert(
              Collections.singleton(new PatchSetApproval(dest, a)));
        }
      }
    }
  }
}
