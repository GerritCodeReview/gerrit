// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.query.change;

import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.LabelTypes;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import java.util.Optional;

public class EqualsLabelPredicate extends ChangeIndexPredicate {
  protected final ProjectCache projectCache;
  protected final PermissionBackend permissionBackend;
  protected final IdentifiedUser.GenericFactory userFactory;
  protected final String label;
  protected final int expVal;
  protected final Account.Id account;
  protected final AccountGroup.UUID group;

  public EqualsLabelPredicate(
      LabelPredicate.Args args, String label, int expVal, Account.Id account) {
    super(ChangeField.LABEL, ChangeField.formatLabel(label, expVal, account));
    this.permissionBackend = args.permissionBackend;
    this.projectCache = args.projectCache;
    this.userFactory = args.userFactory;
    this.group = args.group;
    this.label = label;
    this.expVal = expVal;
    this.account = account;
  }

  @Override
  public boolean match(ChangeData object) {
    Change c = object.change();
    if (c == null) {
      // The change has disappeared.
      //
      return false;
    }

    Optional<ProjectState> project = projectCache.get(c.getDest().project());
    if (!project.isPresent()) {
      // The project has disappeared.
      //
      return false;
    }

    LabelType labelType = type(project.get().getLabelTypes(), label);
    if (labelType == null) {
      return false; // Label is not defined by this project.
    }

    boolean hasVote = false;
    for (PatchSetApproval p : object.currentApprovals()) {
      if (labelType.matches(p)) {
        hasVote = true;
        if (match(object, p.value(), p.accountId())) {
          return true;
        }
      }
    }

    if (!hasVote && expVal == 0) {
      return true;
    }

    return false;
  }

  protected static LabelType type(LabelTypes types, String toFind) {
    if (types.byLabel(toFind) != null) {
      return types.byLabel(toFind);
    }

    for (LabelType lt : types.getLabelTypes()) {
      if (toFind.equalsIgnoreCase(lt.getName())) {
        return lt;
      }
    }
    return null;
  }

  protected boolean match(ChangeData cd, short value, Account.Id approver) {
    if (value != expVal) {
      return false;
    }

    if (account != null && !account.equals(approver)) {
      return false;
    }

    IdentifiedUser reviewer = userFactory.create(approver);
    if (group != null && !reviewer.getEffectiveGroups().contains(group)) {
      return false;
    }

    // Check the user has 'READ' permission.
    try {
      PermissionBackend.ForChange perm = permissionBackend.absentUser(approver).change(cd);
      if (!projectCache.get(cd.project()).map(ProjectState::statePermitsRead).orElse(false)) {
        return false;
      }

      perm.check(ChangePermission.READ);
      return true;
    } catch (PermissionBackendException | AuthException e) {
      return false;
    }
  }

  @Override
  public int getCost() {
    return 1 + (group == null ? 0 : 1);
  }
}
