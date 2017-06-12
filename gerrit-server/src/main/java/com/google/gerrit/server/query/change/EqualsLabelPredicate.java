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
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Provider;

class EqualsLabelPredicate extends ChangeIndexPredicate {
  private final ProjectCache projectCache;
  private final ChangeControl.GenericFactory ccFactory;
  private final IdentifiedUser.GenericFactory userFactory;
  private final Provider<ReviewDb> dbProvider;
  private final String label;
  private final int expVal;
  private final Account.Id account;
  private final AccountGroup.UUID group;

  EqualsLabelPredicate(LabelPredicate.Args args, String label, int expVal, Account.Id account) {
    super(args.field, ChangeField.formatLabel(label, expVal, account));
    this.ccFactory = args.ccFactory;
    this.projectCache = args.projectCache;
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

    ProjectState project = projectCache.get(c.getDest().getParentKey());
    if (project == null) {
      // The project has disappeared.
      //
      return false;
    }

    LabelType labelType = type(project.getLabelTypes(), label);
    if (labelType == null) {
      return false; // Label is not defined by this project.
    }

    boolean hasVote = false;
    for (PatchSetApproval p : object.currentApprovals()) {
      if (labelType.matches(p)) {
        hasVote = true;
        if (match(c, p.getValue(), p.getAccountId(), labelType)) {
          return true;
        }
      }
    }

    if (!hasVote && expVal == 0) {
      return true;
    }

    return false;
  }

  private static LabelType type(LabelTypes types, String toFind) {
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

  private boolean match(Change change, int value, Account.Id approver, LabelType type)
      throws OrmException {
    int psVal = value;
    if (psVal == expVal) {
      // Double check the value is still permitted for the user.
      //
      IdentifiedUser reviewer = userFactory.create(approver);
      try {
        ChangeControl cc = ccFactory.controlFor(dbProvider.get(), change, reviewer);
        if (!cc.isVisible(dbProvider.get())) {
          // The user can't see the change anymore.
          //
          return false;
        }
        psVal = cc.getRange(Permission.forLabel(type.getName())).squash(psVal);
      } catch (NoSuchChangeException e) {
        // The project has disappeared.
        //
        return false;
      }

      if (account != null && !account.equals(approver)) {
        return false;
      }

      if (group != null && !reviewer.getEffectiveGroups().contains(group)) {
        return false;
      }

      if (psVal == expVal) {
        return true;
      }
    }
    return false;
  }

  @Override
  public int getCost() {
    return 1 + (group == null ? 0 : 1);
  }
}
