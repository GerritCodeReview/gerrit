// Copyright (C) 2009 The Android Open Source Project
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

import static com.google.gerrit.reviewdb.client.AccountExternalId.SCHEME_USERNAME;

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountExternalId;
import com.google.gerrit.reviewdb.client.AccountGroup;

import java.util.Collection;
import java.util.Set;

public class AccountState {
  private final Account account;
  private final Set<AccountGroup.UUID> internalGroups;
  private final Collection<AccountExternalId> externalIds;

  public AccountState(final Account account,
      final Set<AccountGroup.UUID> actualGroups,
      final Collection<AccountExternalId> externalIds) {
    this.account = account;
    this.internalGroups = actualGroups;
    this.externalIds = externalIds;
    this.account.setUserName(getUserName(externalIds));
  }

  /** Get the cached account metadata. */
  public Account getAccount() {
    return account;
  }

  /**
   * Get the username, if one has been declared for this user.
   * <p>
   * The username is the {@link AccountExternalId} using the scheme
   * {@link AccountExternalId#SCHEME_USERNAME}.
   */
  public String getUserName() {
    return account.getUserName();
  }

  /** @return the password matching the requested username; or null. */
  public String getPassword(String username) {
    for (AccountExternalId id : getExternalIds()) {
      if (id.isScheme(AccountExternalId.SCHEME_USERNAME)
          && username.equals(id.getSchemeRest())) {
        return id.getPassword();
      }
    }
    return null;
  }

  /** The external identities that identify the account holder. */
  public Collection<AccountExternalId> getExternalIds() {
    return externalIds;
  }

  /** The set of groups maintained directly within the Gerrit database. */
  public Set<AccountGroup.UUID> getInternalGroups() {
    return internalGroups;
  }

  public static String getUserName(Collection<AccountExternalId> ids) {
    for (AccountExternalId id : ids) {
      if (id.isScheme(SCHEME_USERNAME)) {
        return id.getSchemeRest();
      }
    }
    return null;
  }
}
