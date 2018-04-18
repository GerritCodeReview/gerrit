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

import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.LabelTypes;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectAccessor;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Provider;
import java.io.IOException;

public class EqualsLabelPredicate extends ChangeIndexPredicate {
  protected final ProjectAccessor.Factory projectAccessorFactory;
  protected final PermissionBackend permissionBackend;
  protected final IdentifiedUser.GenericFactory userFactory;
  protected final Provider<ReviewDb> dbProvider;
  protected final String label;
  protected final int expVal;
  protected final Account.Id account;
  protected final AccountGroup.UUID group;

  public EqualsLabelPredicate(
      LabelPredicate.Args args, String label, int expVal, Account.Id account) {
    super(ChangeField.LABEL, ChangeField.formatLabel(label, expVal, account));
    this.projectAccessorFactory = args.projectAccessorFactory;
    this.permissionBackend = args.permissionBackend;
    this.userFactory = args.userFactory;
    this.dbProvider = args.dbProvider;
    this.group = args.group;
    this.label = label;
    this.expVal = expVal;
    this.account = account;
  }

  @Override
  public boolean match(ChangeData object) throws OrmException {
    Change c = object.change();
    if (c == null) {
      // The change has disappeared.
      //
      return false;
    }

    LabelType labelType;
    try {
      labelType =
          type(
              projectAccessorFactory
                  .create(c.getDest().getParentKey())
                  .getProjectState()
                  .getLabelTypes(),
              label);
    } catch (NoSuchProjectException | IOException e) {
      // The project has disappeared.
      //
      return false;
    }

    if (labelType == null) {
      return false; // Label is not defined by this project.
    }

    boolean hasVote = false;
    for (PatchSetApproval p : object.currentApprovals()) {
      if (labelType.matches(p)) {
        hasVote = true;
        if (match(object, p.getValue(), p.getAccountId())) {
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
      PermissionBackend.ForChange perm =
          permissionBackend.user(reviewer).database(dbProvider).change(cd);
      return projectAccessorFactory.create(cd.project()).statePermitsRead()
          && perm.test(ChangePermission.READ);
    } catch (NoSuchProjectException | PermissionBackendException | IOException e) {
      return false;
    }
  }

  @Override
  public int getCost() {
    return 1 + (group == null ? 0 : 1);
  }
}
