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

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.AccountsSection;
import com.google.gerrit.entities.PermissionRule;
import com.google.gerrit.exceptions.NoSuchGroupException;
import com.google.gerrit.extensions.common.AccountVisibility;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
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
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

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

  private AccountControl(
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
            return otherUser.account().id();
          }

          @Override
          IdentifiedUser createUser() {
            return userFactory.create(otherUser);
          }
        });
  }

  private boolean canSee(OtherUser otherUser) {
    if (accountVisibility == AccountVisibility.ALL) {
      logger.atFine().log(
          "user %s can see account %d (accountVisibility = %s)",
          user.getLoggableName(), otherUser.getId().get(), AccountVisibility.ALL);
      return true;
    } else if (user.isIdentifiedUser() && user.getAccountId().equals(otherUser.getId())) {
      // I can always see myself.
      logger.atFine().log(
          "user %s can see own account %d", user.getLoggableName(), otherUser.getId().get());
      return true;
    } else if (viewAll()) {
      logger.atFine().log(
          "user %s can see account %d (view all accounts = true)",
          user.getLoggableName(), otherUser.getId().get());
      return true;
    }

    switch (accountVisibility) {
      case SAME_GROUP:
        {
          Set<AccountGroup.UUID> usersGroups = groupsOf(otherUser.getUser());
          for (PermissionRule rule : accountsSection.getSameGroupVisibility()) {
            if (rule.isBlock() || rule.isDeny()) {
              logger.atFine().log(
                  "ignoring group %s of user %s for %s account visibility check"
                      + " because there is a blocked/denied sameGroupVisibility rule: %s",
                  rule.getGroup().getUUID(),
                  otherUser.getUser().getLoggableName(),
                  AccountVisibility.SAME_GROUP,
                  rule);
              usersGroups.remove(rule.getGroup().getUUID());
            }
          }

          if (user.getEffectiveGroups().containsAnyOf(usersGroups)) {
            logger.atFine().log(
                "user %s can see account %d because they share a group (accountVisibility = %s)",
                user.getLoggableName(), otherUser.getId().get(), AccountVisibility.SAME_GROUP);
            return true;
          }

          logger.atFine().log(
              "user %s cannot see account %d because they don't share a group"
                  + " (accountVisibility = %s)",
              user.getLoggableName(), otherUser.getId().get(), AccountVisibility.SAME_GROUP);
          logger.atFine().log("groups of user %s: %s", user.getLoggableName(), groupsOf(user));
          logger.atFine().log(
              "groups of other user %s: %s", otherUser.getUser().getLoggableName(), usersGroups);
          return false;
        }
      case VISIBLE_GROUP:
        {
          Set<AccountGroup.UUID> usersGroups = groupsOf(otherUser.getUser());
          for (AccountGroup.UUID usersGroup : usersGroups) {
            try {
              if (groupControlFactory.controlFor(usersGroup).isVisible()) {
                logger.atFine().log(
                    "user %s can see account %d because it is member of the visible group %s"
                        + " (accountVisibility = %s)",
                    user.getLoggableName(),
                    otherUser.getId().get(),
                    usersGroup.get(),
                    AccountVisibility.VISIBLE_GROUP);
                return true;
              }
            } catch (NoSuchGroupException e) {
              continue;
            }
          }

          logger.atFine().log(
              "user %s cannot see account %d because none of its groups are visible"
                  + " (accountVisibility = %s)",
              user.getLoggableName(), otherUser.getId().get(), AccountVisibility.VISIBLE_GROUP);
          logger.atFine().log(
              "groups of other user %s: %s", otherUser.getUser().getLoggableName(), usersGroups);
          return false;
        }
      case NONE:
        logger.atFine().log(
            "user %s cannot see account %d (accountVisibility = %s)",
            user.getLoggableName(), otherUser.getId().get(), AccountVisibility.NONE);
        return false;
      case ALL:
      default:
        throw new IllegalStateException("Bad AccountVisibility " + accountVisibility);
    }
  }

  private boolean viewAll() {
    if (viewAll == null) {
      try {
        perm.check(GlobalPermission.VIEW_ALL_ACCOUNTS);
        viewAll = true;
      } catch (AuthException e) {
        viewAll = false;
      } catch (PermissionBackendException e) {
        logger.atFine().withCause(e).log(
            "Failed to check %s global capability for user %s",
            GlobalPermission.VIEW_ALL_ACCOUNTS, user.getLoggableName());
        viewAll = false;
      }
    }
    return viewAll;
  }

  private Set<AccountGroup.UUID> groupsOf(CurrentUser user) {
    return user.getEffectiveGroups().getKnownGroups().stream()
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
