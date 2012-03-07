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

import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

/** Access control management for one account's access to other accounts. */
public class AccountControl {
  private static final Logger log =
      LoggerFactory.getLogger(AccountControl.class);

  public static class Factory {
    private final GroupControl.Factory groupControlFactory;
    private final Provider<CurrentUser> user;
    private final IdentifiedUser.GenericFactory userFactory;
    private final AccountVisibility accountVisibility;

    @Inject
    Factory(final GroupControl.Factory groupControlFactory,
        final Provider<CurrentUser> user,
        final IdentifiedUser.GenericFactory userFactory,
        @GerritServerConfig final Config cfg) {
      this.groupControlFactory = groupControlFactory;
      this.user = user;
      this.userFactory = userFactory;

      AccountVisibility av;
      if (cfg.getString("accounts", null, "visibility") != null) {
        av = cfg.getEnum("accounts", null, "visibility", AccountVisibility.ALL);
      } else {
        try {
          av = cfg.getEnum("suggest", null, "accounts", AccountVisibility.ALL);
          log.warn(String.format(
              "Using legacy value %s for suggest.accounts;"
              + " use accounts.visibility=%s instead",
              av, av));
        } catch (IllegalArgumentException err) {
          // If suggest.accounts is a valid boolean, it's a new-style config, and
          // we should use the default here. Invalid values are caught in
          // SuggestServiceImpl so we don't worry about them here.
          av = AccountVisibility.ALL;
        }
      }
      accountVisibility = av;
    }

    public AccountControl get() {
      return new AccountControl(groupControlFactory, user.get(), userFactory,
          accountVisibility);
    }
  }

  private final GroupControl.Factory groupControlFactory;
  private final CurrentUser currentUser;
  private final IdentifiedUser.GenericFactory userFactory;
  private final AccountVisibility accountVisibility;

  AccountControl(final GroupControl.Factory groupControlFactory,
        final CurrentUser currentUser,
        final IdentifiedUser.GenericFactory userFactory,
        final AccountVisibility accountVisibility) {
    this.groupControlFactory = groupControlFactory;
    this.currentUser = currentUser;
    this.userFactory = userFactory;
    this.accountVisibility = accountVisibility;
  }

  public boolean canSee(final Account otherUser) {
    // Special case: I can always see myself.
    if (currentUser instanceof IdentifiedUser
        && ((IdentifiedUser) currentUser).getAccountId()
            .equals(otherUser.getId())) {
      return true;
    }

    switch (accountVisibility) {
      case ALL:
        return true;
      case SAME_GROUP: {
        Set<AccountGroup.UUID> usersGroups = groupsOf(otherUser);
        usersGroups.remove(AccountGroup.ANONYMOUS_USERS);
        usersGroups.remove(AccountGroup.REGISTERED_USERS);
        for (AccountGroup.UUID myGroup : currentUser.getEffectiveGroups()) {
          if (usersGroups.contains(myGroup)) {
            return true;
          }
        }
        break;
      }
      case VISIBLE_GROUP: {
        Set<AccountGroup.UUID> usersGroups = groupsOf(otherUser);
        usersGroups.remove(AccountGroup.ANONYMOUS_USERS);
        usersGroups.remove(AccountGroup.REGISTERED_USERS);
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
      default:
        throw new IllegalStateException("Bad AccountVisibility " + accountVisibility);
    }
    return false;
  }

  private Set<AccountGroup.UUID> groupsOf(Account account) {
    IdentifiedUser user = userFactory.create(account.getId());
    return new HashSet<AccountGroup.UUID>(user.getEffectiveGroups());
  }
}
