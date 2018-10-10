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
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;

@Singleton
public class RemoveReviewerControl {
  private final PermissionBackend permissionBackend;
  private final Provider<ReviewDb> dbProvider;
  private final ProjectControl.GenericFactory projectControlFactory;

  @Inject
  RemoveReviewerControl(
      PermissionBackend permissionBackend,
      Provider<ReviewDb> dbProvider,
      ProjectControl.GenericFactory projectControlFactory) {
    this.permissionBackend = permissionBackend;
    this.dbProvider = dbProvider;
    this.projectControlFactory = projectControlFactory;
  }

  /**
   * Checks if removing the given reviewer and patch set approval is OK.
   *
   * @throws AuthException if this user is not allowed to remove this approval.
   */
  public void checkRemoveReviewer(
      ChangeNotes notes, CurrentUser currentUser, PatchSetApproval approval)
      throws PermissionBackendException, AuthException, NoSuchProjectException, IOException {
    checkRemoveReviewer(notes, currentUser, approval.getAccountId(), approval.getValue());
  }

  /**
   * Checks if removing the given reviewer is OK. Does not check if removing any approvals the
   * reviewer might have given is OK.
   *
   * @throws AuthException if this user is not allowed to remove this approval.
   */
  public void checkRemoveReviewer(ChangeNotes notes, CurrentUser currentUser, Account.Id reviewer)
      throws PermissionBackendException, AuthException, NoSuchProjectException, IOException {
    checkRemoveReviewer(notes, currentUser, reviewer, 0);
  }

  /** @return true if the user is allowed to remove this reviewer. */
  public boolean testRemoveReviewer(
      ChangeData cd, CurrentUser currentUser, Account.Id reviewer, int value)
      throws PermissionBackendException, NoSuchProjectException, OrmException, IOException {
    if (canRemoveReviewerWithoutPermissionCheck(cd.change(), currentUser, reviewer, value)) {
      return true;
    }
    return permissionBackend
        .user(currentUser)
        .change(cd)
        .database(dbProvider)
        .test(ChangePermission.REMOVE_REVIEWER);
  }

  private void checkRemoveReviewer(
      ChangeNotes notes, CurrentUser currentUser, Account.Id reviewer, int val)
      throws PermissionBackendException, NoSuchProjectException, AuthException, IOException {
    if (canRemoveReviewerWithoutPermissionCheck(notes.getChange(), currentUser, reviewer, val)) {
      return;
    }

    permissionBackend
        .user(currentUser)
        .change(notes)
        .database(dbProvider)
        .check(ChangePermission.REMOVE_REVIEWER);
  }

  private boolean canRemoveReviewerWithoutPermissionCheck(
      Change change, CurrentUser currentUser, Account.Id reviewer, int value)
      throws NoSuchProjectException, IOException {
    if (change.getStatus().equals(Change.Status.MERGED)) {
      return false;
    }

    if (currentUser.isIdentifiedUser()) {
      Account.Id aId = currentUser.getAccountId();
      if (aId.equals(reviewer)) {
        return true; // A user can always remove themselves.
      } else if (aId.equals(change.getOwner()) && 0 <= value) {
        return true; // The change owner may remove any zero or positive score.
      }
    }

    // Users with the remove reviewer permission, the branch owner, project
    // owner and site admin can remove anyone
    ProjectControl ctl = projectControlFactory.controlFor(change.getProject(), currentUser);
    if (ctl.controlForRef(change.getDest()).isOwner() // branch owner
        || ctl.isOwner() // project owner
        || ctl.isAdmin()) { // project admin
      return true;
    }
    return false;
  }
}
