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

package com.google.gerrit.server.project;

import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.LabelRemovalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.inject.Inject;
import java.util.Set;

public class DeleteVoteControl {
  private final PermissionBackend permissionBackend;

  @Inject
  public DeleteVoteControl(PermissionBackend permissionBackend) {
    this.permissionBackend = permissionBackend;
  }

  public void checkDeleteVotePermissions(
      CurrentUser user, ChangeNotes notes, PatchSetApproval approval, LabelType labelType)
      throws AuthException, PermissionBackendException {
    if (testDeleteVotePermissions(user, notes, approval, labelType)) {
      return;
    }
    throw new AuthException(
        new LabelRemovalPermission.WithValue(labelType, approval.value()).describeForException()
            + " not permitted");
  }

  public boolean testDeleteVotePermissions(
      CurrentUser user, ChangeNotes notes, PatchSetApproval approval, LabelType labelType)
      throws PermissionBackendException {
    if (canRemoveReviewerWithoutRemoveLabelPermission(
        notes.getChange(), user, approval.accountId(), approval.value())) {
      return true;
    }
    // Test if the user is allowed to remove vote of the given label type and value.
    Set<LabelRemovalPermission.WithValue> allowed =
        permissionBackend.user(user).change(notes).testRemoval(labelType);
    return allowed.contains(new LabelRemovalPermission.WithValue(labelType, approval.value()));
  }

  private boolean canRemoveReviewerWithoutRemoveLabelPermission(
      Change change, CurrentUser user, Account.Id reviewer, int value)
      throws PermissionBackendException {
    if (user.isIdentifiedUser()) {
      Account.Id aId = user.getAccountId();
      if (aId.equals(reviewer)) {
        return true; // A user can always remove their own votes.
      } else if (aId.equals(change.getOwner()) && 0 <= value) {
        return true; // The change owner may remove any zero or positive score.
      }
    }

    // Users with the remove reviewer permission, the branch owner, project
    // owner and site admin can remove anyone
    PermissionBackend.WithUser withUser = permissionBackend.user(user);
    PermissionBackend.ForProject forProject = withUser.project(change.getProject());
    return forProject.ref(change.getDest().branch()).test(RefPermission.WRITE_CONFIG)
        || withUser.test(GlobalPermission.ADMINISTRATE_SERVER);
  }
}
