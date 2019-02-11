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
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;

@Singleton
public class RemoveReviewerControl {
  private final ApprovalsUtil approvalsUtil;
  private final PermissionBackend permissionBackend;

  @Inject
  RemoveReviewerControl(PermissionBackend permissionBackend, ApprovalsUtil approvalsUtil) {
    this.permissionBackend = permissionBackend;
    this.approvalsUtil = approvalsUtil;
  }

  /**
   * Checks if removing the given reviewer and patch set approval is OK.
   *
   * @throws AuthException if this user is not allowed to remove this approval.
   * @throws PermissionBackendException on failure of permission checks.
   */
  public void checkRemoveReviewer(
      ChangeNotes notes, CurrentUser currentUser, PatchSetApproval approval)
      throws PermissionBackendException, AuthException, IOException, OrmException {
    checkRemoveReviewer(notes, currentUser, approval.getAccountId(), approval.getValue());
  }

  /**
   * Checks if removing the given reviewer is OK. Does not check if removing any approvals the
   * reviewer might have given is OK.
   *
   * @throws AuthException if this user is not allowed to remove this approval.
   * @throws PermissionBackendException on failure of permission checks.
   */
  public void checkRemoveReviewer(ChangeNotes notes, CurrentUser currentUser, Account.Id reviewer)
      throws PermissionBackendException, AuthException, IOException, OrmException {
    checkRemoveReviewer(notes, currentUser, reviewer, 0);
  }

  /** @return true if the user is allowed to remove this reviewer. */
  public boolean testRemoveReviewer(
      ChangeData cd, CurrentUser currentUser, Account.Id reviewer, int value)
      throws PermissionBackendException, OrmException, IOException {
    boolean isLabelLocked = approvalsUtil.isLabelLocked(cd.notes());
    if (canRemoveReviewerWithoutPermissionCheck(
        permissionBackend, cd.change(), currentUser, reviewer, value, isLabelLocked)) {
      return true;
    }
    if (isLabelLocked) {
      return false;
    }
    return permissionBackend.user(currentUser).change(cd).test(ChangePermission.REMOVE_REVIEWER);
  }

  private void checkRemoveReviewer(
      ChangeNotes notes, CurrentUser currentUser, Account.Id reviewer, int val)
      throws PermissionBackendException, AuthException, IOException, OrmException {
    boolean isLabelLocked = approvalsUtil.isLabelLocked(notes);
    if (canRemoveReviewerWithoutPermissionCheck(
        permissionBackend, notes.getChange(), currentUser, reviewer, val, isLabelLocked)) {
      return;
    }
    if (isLabelLocked) {
      throw new AuthException("not allowed to remove reviewer since label lock is set");
    }

    permissionBackend.user(currentUser).change(notes).check(ChangePermission.REMOVE_REVIEWER);
  }

  private static boolean canRemoveReviewerWithoutPermissionCheck(
      PermissionBackend permissionBackend,
      Change change,
      CurrentUser currentUser,
      Account.Id reviewer,
      int value,
      boolean isLabelLocked)
      throws PermissionBackendException {
    if (change.getStatus().equals(Change.Status.MERGED)) {
      return false;
    }
    // If label locked, users shouldn't be able to remove labels
    // regardless of whether they own the change or whether they are
    // removing their own labels
    if (!isLabelLocked && currentUser.isIdentifiedUser()) {
      Account.Id aId = currentUser.getAccountId();
      if (aId.equals(reviewer)) {
        return true; // A user can always remove themselves.
      } else if (aId.equals(change.getOwner()) && 0 <= value) {
        return true; // The change owner may remove any zero or positive score.
      }
    }

    // Users with the remove reviewer permission, the branch owner, project
    // owner and site admin can remove anyone
    PermissionBackend.WithUser withUser = permissionBackend.user(currentUser);
    PermissionBackend.ForProject forProject = withUser.project(change.getProject());
    if (check(forProject.ref(change.getDest().get()), RefPermission.WRITE_CONFIG)
        || check(withUser, GlobalPermission.ADMINISTRATE_SERVER)) {
      return true;
    }
    return false;
  }

  private static boolean check(PermissionBackend.ForRef forRef, RefPermission perm)
      throws PermissionBackendException {
    try {
      forRef.check(perm);
      return true;
    } catch (AuthException e) {
      return false;
    }
  }

  private static boolean check(PermissionBackend.WithUser withUser, GlobalPermission perm)
      throws PermissionBackendException {
    try {
      withUser.check(perm);
      return true;
    } catch (AuthException e) {
      return false;
    }
  }
}
