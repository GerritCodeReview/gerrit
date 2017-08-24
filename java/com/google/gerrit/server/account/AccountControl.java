// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.server.account;

import static java.util.stream.Collectors.toSet;

import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.AccountsSection;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.Set;

/** Access control management for one account's access to other accounts. */
public class AccountControl {
  public static class Factory {
    private final PermissionBackend permissionBackend;
    private final ProjectCache projectCache;
    private final GroupControl.Factory groupControlFactory;
    private final Provider<CurrentUser> user;
    private final IdentifiedUser.GenericFactory userFactory;
    private final AccountVisibility accountVisibility;

    @Inject
    Factory(
        PermissionBackend permissionBackend,
        ProjectCache projectCache,
        GroupControl.Factory groupControlFactory,
        Provider<CurrentUser> user,
        IdentifiedUser.GenericFactory userFactory,
        AccountVisibility accountVisibility) {
      this.permissionBackend = permissionBackend;
      this.projectCache = projectCache;
      this.groupControlFactory = groupControlFactory;
      this.user = user;
      this.userFactory = userFactory;
      this.accountVisibility = accountVisibility;
    }

    public AccountControl get() {
      return new AccountControl(
          permissionBackend,
          projectCache,
          groupControlFactory,
          user.get(),
          userFactory,
          accountVisibility);
    }
  }

  private final AccountsSection accountsSection;
  private final GroupControl.Factory groupControlFactory;
  private final PermissionBackend.WithUser perm;
  private final CurrentUser user;
  private final IdentifiedUser.GenericFactory userFactory;
  private final AccountVisibility accountVisibility;

  private Boolean viewAll;

  AccountControl(
      PermissionBackend permissionBackend,
      ProjectCache projectCache,
      GroupControl.Factory groupControlFactory,
      CurrentUser user,
      IdentifiedUser.GenericFactory userFactory,
      AccountVisibility accountVisibility) {
    this.accountsSection = projectCache.getAllProjects().getConfig().getAccountsSection();
    this.groupControlFactory = groupControlFactory;
    this.perm = permissionBackend.user(user);
    this.user = user;
    this.userFactory = userFactory;
    this.accountVisibility = accountVisibility;
  }

  public CurrentUser getUser() {
    return user;
  }

  /**
   * Returns true if the current user is allowed to see the otherUser, based on the account
   * visibility policy. Depending on the group membership realms supported, this may not be able to
   * determine SAME_GROUP or VISIBLE_GROUP correctly (defaulting to not being visible). This is
   * because {@link GroupMembership#getKnownGroups()} may only return a subset of the effective
   * groups.
   */
  public boolean canSee(Account otherUser) {
    return canSee(otherUser.getId());
  }

  /**
   * Returns true if the current user is allowed to see the otherUser, based on the account
   * visibility policy. Depending on the group membership realms supported, this may not be able to
   * determine SAME_GROUP or VISIBLE_GROUP correctly (defaulting to not being visible). This is
   * because {@link GroupMembership#getKnownGroups()} may only return a subset of the effective
   * groups.
   */
  public boolean canSee(Account.Id otherUser) {
    return canSee(
        new OtherUser() {
          @Override
          Account.Id getId() {
            return otherUser;
          }

          @Override
          IdentifiedUser createUser() {
            return userFactory.create(otherUser);
          }
        });
  }

  /**
   * Returns true if the current user is allowed to see the otherUser, based on the account
   * visibility policy. Depending on the group membership realms supported, this may not be able to
   * determine SAME_GROUP or VISIBLE_GROUP correctly (defaulting to not being visible). This is
   * because {@link GroupMembership#getKnownGroups()} may only return a subset of the effective
   * groups.
   */
  public boolean canSee(AccountState otherUser) {
    return canSee(
        new OtherUser() {
          @Override
          Account.Id getId() {
            return otherUser.getAccount().getId();
          }

          @Override
          IdentifiedUser createUser() {
            return userFactory.create(otherUser);
          }
        });
  }

  private boolean canSee(OtherUser otherUser) {
    if (accountVisibility == AccountVisibility.ALL) {
      return true;
    } else if (user.isIdentifiedUser() && user.getAccountId().equals(otherUser.getId())) {
      // I can always see myself.
      return true;
    } else if (viewAll()) {
      return true;
    }

    switch (accountVisibility) {
      case SAME_GROUP:
        {
          Set<AccountGroup.UUID> usersGroups = groupsOf(otherUser.getUser());
          for (PermissionRule rule : accountsSection.getSameGroupVisibility()) {
            if (rule.isBlock() || rule.isDeny()) {
              usersGroups.remove(rule.getGroup().getUUID());
            }
          }

          if (user.getEffectiveGroups().containsAnyOf(usersGroups)) {
            return true;
          }
          break;
        }
      case VISIBLE_GROUP:
        {
          Set<AccountGroup.UUID> usersGroups = groupsOf(otherUser.getUser());
          for (AccountGroup.UUID usersGroup : usersGroups) {
            try {
              if (groupControlFactory.controlFor(usersGroup).isVisible()) {
                return true;
              }
            } catch (NoSuchGroupException e) {
              continue;
            }
          }
          break;
        }
      case NONE:
        break;
      case ALL:
      default:
        throw new IllegalStateException("Bad AccountVisibility " + accountVisibility);
    }
    return false;
  }

  private boolean viewAll() {
    if (viewAll == null) {
      try {
        perm.check(GlobalPermission.VIEW_ALL_ACCOUNTS);
        viewAll = true;
      } catch (AuthException | PermissionBackendException e) {
        viewAll = false;
      }
    }
    return viewAll;
  }

  private Set<AccountGroup.UUID> groupsOf(IdentifiedUser user) {
    return user.getEffectiveGroups()
        .getKnownGroups()
        .stream()
        .filter(a -> !SystemGroupBackend.isSystemGroup(a))
        .collect(toSet());
  }

  private abstract static class OtherUser {
    IdentifiedUser user;

    IdentifiedUser getUser() {
      if (user == null) {
        user = createUser();
      }
      return user;
    }

    abstract IdentifiedUser createUser();

    abstract Account.Id getId();
  }
}
