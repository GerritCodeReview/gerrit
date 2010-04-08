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

package com.google.gerrit.httpd.rpc.patch;

import com.google.gerrit.common.data.ReviewerResult;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.httpd.rpc.changedetail.ChangeDetailFactory;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.PatchSetApproval;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.Change.Id;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.ArrayList;
import java.util.List;

/**
 * Implement the remote logic that removes a reviewer from a change.
 *
 * @author Cedric Beust (cbeust@google.com)
 */
class RemoveReviewer extends Handler<ReviewerResult> {
  interface Factory {
    RemoveReviewer create(Change.Id changeId, Account.Id reviewerId);
  }

  private final Account.Id reviewerId;
  private final ChangeControl.Factory changeControlFactory;
  private final ReviewDb db;
  private final Id changeId;
  private final ChangeDetailFactory.Factory changeDetailFactory;

  @Inject
  RemoveReviewer(final ReviewDb db, final ChangeControl.Factory changeControlFactory,
      final ChangeDetailFactory.Factory changeDetailFactory,
      @Assisted Change.Id changeId, @Assisted Account.Id reviewerId) {
    this.db = db;
    this.changeControlFactory = changeControlFactory;
    this.changeId = changeId;
    this.reviewerId = reviewerId;
    this.changeDetailFactory = changeDetailFactory;
  }

  @Override
  public ReviewerResult call() throws Exception {
    ReviewerResult result = new ReviewerResult();
    List<Account.Id> accounts = new ArrayList<Account.Id>();
    ChangeControl ctl = changeControlFactory.validateFor(changeId);
    if (ctl.canRemoveReviewer(reviewerId)) {
      Iterable<PatchSetApproval> patchSets = db.patchSetApprovals().byChange(changeId);
      List<PatchSetApproval> toDelete = new ArrayList<PatchSetApproval>();
      for (PatchSetApproval psa : patchSets) {
        if (psa.getAccountId().equals(reviewerId)) {
          toDelete.add(psa);
        }
      }
      try {
        db.patchSetApprovals().delete(toDelete);
      } catch(OrmException ex) {
        result.addError(new ReviewerResult.Error(
            ReviewerResult.Error.Type.COULD_NOT_REMOVE, "Could not remove reviewer " + reviewerId));
      }
    } else {
      result.addError(new ReviewerResult.Error(
          ReviewerResult.Error.Type.COULD_NOT_REMOVE, "Not allowed to remove reviewer "
              + reviewerId));
    }

    // Note: call setChange() after the deletion has been made or it will still
    // contain the reviewer we want to delete.
    result.setChange(changeDetailFactory.create(changeId).call());
    return result;
  }

}
