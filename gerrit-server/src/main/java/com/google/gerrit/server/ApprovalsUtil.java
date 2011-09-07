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

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gwtorm.server.OrmException;

import java.util.List;
import java.util.ArrayList;

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

  public static void clearCurrentPatchSetApprovals(final ReviewDb db,
      final Change change) throws OrmException {
    List<PatchSetApproval> reviews = getReviews(db.patchSetApprovals()
        .byPatchSet(change.currentPatchSetId()).toList());
    for (PatchSetApproval r : reviews) {
      // FIXME: Need to figure out how to get approvalTypes here
      final ApprovalType type = approvalTypes.byId(r.getCategoryId());
      if (type.getCategory().isCopyMinScore() && type.isMaxNegative(r)) {
        // If there was a negative vote, carry it to the new branch.
        continue;
      }
      // Setting to 0 instead of deleting ensures that they are still CCed
      r.setValue((short) 0);
    }
    db.patchSetApprovals().update(reviews);
  }

  public static List<PatchSetApproval> getReviews(List<PatchSetApproval>
       approvals) {
    List<PatchSetApproval> reviews = new ArrayList<PatchSetApproval>(
        approvals.size());
    for (PatchSetApproval a : approvals) {
      if (a.getValue() != 0) {
        reviews.add(a);
      }
    }
    return reviews;
  }
}
