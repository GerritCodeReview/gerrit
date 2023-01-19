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
import com.google.gerrit.index.query.PostFilterPredicate;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import java.util.Optional;

public class EqualsLabelPredicates {
  public static class PostFilterEqualsLabelPredicate extends PostFilterPredicate<ChangeData> {
    private final Matcher matcher;

    public PostFilterEqualsLabelPredicate(LabelPredicate.Args args, String label, int expVal) {
      super(ChangeQueryBuilder.FIELD_LABEL, ChangeField.formatLabel(label, expVal));
      matcher = new Matcher(args, label, expVal);
    }

    @Override
    public boolean match(ChangeData object) {
      return matcher.match(object);
    }

    @Override
    public int getCost() {
      return 2;
    }
  }

  public static class IndexEqualsLabelPredicate extends ChangeIndexPostFilterPredicate {
    private final Matcher matcher;

    public IndexEqualsLabelPredicate(LabelPredicate.Args args, String label, int expVal) {
      this(args, label, expVal, null);
    }

    public IndexEqualsLabelPredicate(
        LabelPredicate.Args args, String label, int expVal, Account.Id account) {
      super(ChangeField.LABEL, ChangeField.formatLabel(label, expVal, account));
      this.matcher = new Matcher(args, label, expVal, account);
    }

    @Override
    public boolean match(ChangeData object) {
      return matcher.match(object);
    }

    @Override
    public int getCost() {
      return 1 + (matcher.group == null ? 0 : 1);
    }
  }

  private static class Matcher {
    protected final ProjectCache projectCache;
    protected final PermissionBackend permissionBackend;
    protected final IdentifiedUser.GenericFactory userFactory;
    protected final String label;
    protected final int expVal;
    protected final Account.Id account;
    protected final AccountGroup.UUID group;

    public Matcher(LabelPredicate.Args args, String label, int expVal) {
      this(args, label, expVal, null);
    }

    public Matcher(LabelPredicate.Args args, String label, int expVal, Account.Id account) {
      this.permissionBackend = args.permissionBackend;
      this.projectCache = args.projectCache;
      this.userFactory = args.userFactory;
      this.group = args.group;
      this.label = label;
      this.expVal = expVal;
      this.account = account;
    }

    public boolean match(ChangeData cd) {
      Change c = cd.change();
      if (c == null) {
        // The change has disappeared.
        return false;
      }

      Optional<ProjectState> project = projectCache.get(c.getDest().project());
      if (!project.isPresent()) {
        // The project has disappeared.
        return false;
      }

      LabelType labelType = type(project.get().getLabelTypes(), label);
      if (labelType == null) {
        return false; // Label is not defined by this project.
      }

      boolean hasVote = false;
      cd.setStorageConstraint(ChangeData.StorageConstraint.INDEX_PRIMARY_NOTEDB_SECONDARY);
      for (PatchSetApproval psa : cd.currentApprovals()) {
        if (labelType.matches(psa)) {
          hasVote = true;
          if (match(cd, psa)) {
            return true;
          }
        }
      }

      if (!hasVote && expVal == 0) {
        return true;
      }

      return false;
    }

    private boolean match(ChangeData cd, PatchSetApproval psa) {
      if (psa.value() != expVal) {
        return false;
      }
      Account.Id approver = psa.accountId();

      if (account != null) {
        // case when account in query is numeric
        if (!account.equals(approver) && !isMagicUser()) {
          return false;
        }

        // case when account in query = owner
        if (account.equals(ChangeQueryBuilder.OWNER_ACCOUNT_ID)
            && !cd.change().getOwner().equals(approver)) {
          return false;
        }

        // case when account in query = non_uploader
        if (account.equals(ChangeQueryBuilder.NON_UPLOADER_ACCOUNT_ID)
            && cd.currentPatchSet().uploader().equals(approver)) {
          return false;
        }
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

    private boolean isMagicUser() {
      return account.equals(ChangeQueryBuilder.OWNER_ACCOUNT_ID)
          || account.equals(ChangeQueryBuilder.NON_UPLOADER_ACCOUNT_ID);
    }
  }

  public static LabelType type(LabelTypes types, String toFind) {
    if (types.byLabel(toFind).isPresent()) {
      return types.byLabel(toFind).get();
    }

    for (LabelType lt : types.getLabelTypes()) {
      if (toFind.equalsIgnoreCase(lt.getName())) {
        return lt;
      }
    }
    return null;
  }
}
