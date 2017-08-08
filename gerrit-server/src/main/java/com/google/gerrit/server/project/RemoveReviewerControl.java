// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.project;

import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;

public class RemoveReviewerControl {

  public interface Factory {
    RemoveReviewerControl create(ChangeControl ctl);
  }

  private final ChangeControl changeControl;
  private final PermissionBackend.ForChange forChange;

  @Inject
  RemoveReviewerControl(
      PermissionBackend permissionBackend, Provider<ReviewDb> db, @Assisted ChangeControl ctl) {
    this.forChange = permissionBackend.user(ctl.getUser()).database(db).change(ctl.getNotes());
    this.changeControl = ctl;
  }

  /** @throws AuthException if this user is not allowed to remove this approval. */
  public void checkRemoveReviewer(PatchSetApproval approval)
      throws PermissionBackendException, AuthException {
    if (canRemoveReviewerWithoutPermissionCheck(approval.getAccountId(), approval.getValue())) {
      return;
    }
    forChange.check(ChangePermission.REMOVE_REVIEWER);
  }

  /** @return true if the user is allowed to remove this reviewer. */
  public boolean testRemoveReviewer(Account.Id reviewer, int value)
      throws PermissionBackendException {
    if (canRemoveReviewerWithoutPermissionCheck(reviewer, value)) {
      return true;
    }
    return forChange.test(ChangePermission.REMOVE_REVIEWER);
  }

  private boolean canRemoveReviewerWithoutPermissionCheck(Account.Id reviewer, int value) {
    if (!changeControl.getChange().getStatus().isOpen()) {
      return false;
    }
    // A user can always remove themselves.
    if (changeControl.getUser().isIdentifiedUser()) {
      if (changeControl.getUser().getAccountId().equals(reviewer)) {
        return true; // can remove self
      }
    }
    // The change owner may remove any zero or positive score.
    if (changeControl.isOwner() && 0 <= value) {
      return true;
    }
    // Users with the remove reviewer permission, the branch owner, project
    // owner and site admin can remove anyone
    if (changeControl.getRefControl().isOwner() // branch owner
        || changeControl.getProjectControl().isOwner() // project owner
        || changeControl.getProjectControl().isAdmin()) { // project admin
      return true;
    }
    return false;
  }
}
