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
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

@Singleton
public class RemoveReviewerControl {
  private final PermissionBackend permissionBackend;
  private final Provider<ReviewDb> dbProvider;
  private final ChangeControl.GenericFactory changeControlFactory;

  @Inject
  RemoveReviewerControl(
      PermissionBackend permissionBackend,
      Provider<ReviewDb> dbProvider,
      ChangeControl.GenericFactory changeControlFactory) {
    this.permissionBackend = permissionBackend;
    this.dbProvider = dbProvider;
    this.changeControlFactory = changeControlFactory;
  }

  /** @throws AuthException if this user is not allowed to remove this approval. */
  public void checkRemoveReviewer(
      ChangeNotes notes, CurrentUser currentUser, PatchSetApproval approval)
      throws PermissionBackendException, AuthException, NoSuchChangeException {
    if (canRemoveReviewerWithoutPermissionCheck(
        notes, currentUser, approval.getAccountId(), approval.getValue())) {
      return;
    }

    permissionBackend
        .user(currentUser)
        .change(notes)
        .database(dbProvider)
        .check(ChangePermission.REMOVE_REVIEWER);
  }

  /** @return true if the user is allowed to remove this reviewer. */
  public boolean testRemoveReviewer(
      ChangeNotes notes, CurrentUser currentUser, Account.Id reviewer, int value)
      throws PermissionBackendException, NoSuchChangeException {
    if (canRemoveReviewerWithoutPermissionCheck(notes, currentUser, reviewer, value)) {
      return true;
    }
    return permissionBackend
        .user(currentUser)
        .change(notes)
        .database(dbProvider)
        .test(ChangePermission.REMOVE_REVIEWER);
  }

  private boolean canRemoveReviewerWithoutPermissionCheck(
      ChangeNotes notes, CurrentUser currentUser, Account.Id reviewer, int value)
      throws NoSuchChangeException {
    ChangeControl changeControl = changeControlFactory.controlFor(notes, currentUser);
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
