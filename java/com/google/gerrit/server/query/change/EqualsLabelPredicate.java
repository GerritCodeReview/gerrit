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

import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.LabelTypes;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.account.AccountPredicates;
import com.google.gerrit.server.query.account.InternalAccountQuery;
import com.google.gerrit.server.query.change.ChangeData.StorageConstraint;
import com.google.inject.Provider;
import java.util.List;
import java.util.Optional;

public class EqualsLabelPredicate extends ChangeIndexPostFilterPredicate {
  protected final Provider<InternalAccountQuery> queryProcessorProvider;
  protected final ProjectCache projectCache;
  protected final PermissionBackend permissionBackend;
  protected final IdentifiedUser.GenericFactory userFactory;
  /** label name to be matched. */
  protected final String label;

  /** Expected vote value for the label. */
  protected final int expVal;

  /**
   * Number of times the value {@link #expVal} for label {@link #label} should occur. If null, match
   * with any count greater or equal to 1.
   */
  @Nullable protected final Integer count;

  /** Account ID that has voted on the label. */
  protected final Account.Id account;

  protected final AccountGroup.UUID group;

  public EqualsLabelPredicate(
      LabelPredicate.Args args,
      String label,
      int expVal,
      Account.Id account,
      @Nullable Integer count) {
    super(ChangeField.LABEL_SPEC, ChangeField.formatLabel(label, expVal, account, count));
    this.permissionBackend = args.permissionBackend;
    this.queryProcessorProvider = args.queryProcessorProvider;
    this.projectCache = args.projectCache;
    this.userFactory = args.userFactory;
    this.count = count;
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

    if (Integer.valueOf(0).equals(count)) {
      // We don't match against count=0 so that the computation is identical to the stored values
      // in the index. We do that since computing count=0 requires looping on all {label_type,
      // vote_value} for the change and storing a {count=0} format for it in the change index which
      // is computationally expensive.
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
    int matchingVotes = 0;
    StorageConstraint currentStorageConstraint = object.getStorageConstraint();
    object.setStorageConstraint(ChangeData.StorageConstraint.INDEX_PRIMARY_NOTEDB_SECONDARY);
    for (PatchSetApproval p : object.currentApprovals()) {
      if (labelType.matches(p)) {
        hasVote = true;
        if (match(object, p.value(), p.accountId())) {
          matchingVotes += 1;
        }
      }
    }
    object.setStorageConstraint(currentStorageConstraint);
    if (!hasVote && expVal == 0) {
      return true;
    }

    return count == null ? matchingVotes >= 1 : matchingVotes == count;
  }

  @Nullable
  protected static LabelType type(LabelTypes types, String toFind) {
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

  protected boolean match(ChangeData cd, short value, Account.Id approver) {
    if (value != expVal) {
      return false;
    }

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

      if (account.equals(ChangeQueryBuilder.NON_CONTRIBUTOR_ACCOUNT_ID)) {
        if ((cd.currentPatchSet().uploader().equals(approver)
            || matchAccount(cd.getCommitter().getEmailAddress(), approver)
            || matchAccount(cd.getAuthor().getEmailAddress(), approver))) {
          return false;
        }
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

  /**
   * Returns true if the {@code email} parameter belongs to the account identified by the {@code
   * accountId} parameter.
   */
  private boolean matchAccount(String email, Account.Id accountId) {
    List<AccountState> accountsList =
        queryProcessorProvider.get().query(AccountPredicates.preferredEmail(email));
    return accountsList.stream().anyMatch(c -> c.account().id().equals(accountId));
  }

  private boolean isMagicUser() {
    return account.equals(ChangeQueryBuilder.OWNER_ACCOUNT_ID)
        || account.equals(ChangeQueryBuilder.NON_UPLOADER_ACCOUNT_ID)
        || account.equals(ChangeQueryBuilder.NON_CONTRIBUTOR_ACCOUNT_ID);
  }

  @Override
  public int getCost() {
    return 1 + (group == null ? 0 : 1);
  }
}
