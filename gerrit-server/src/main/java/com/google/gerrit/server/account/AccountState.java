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

import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountExternalId;
import com.google.gerrit.reviewdb.AccountGroup;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class AccountState {
  private final Account account;
  private final Set<AccountGroup.Id> internalGroups;
  private final Collection<AccountExternalId> externalIds;

  public AccountState(final Account account,
      final Set<AccountGroup.Id> actualGroups,
      final Collection<AccountExternalId> externalIds) {
    this.account = account;
    this.internalGroups = actualGroups;
    this.externalIds = externalIds;
  }

  /** Get the cached account metadata. */
  public Account getAccount() {
    return account;
  }

  /**
   * All email addresses registered to this account.
   * <p>
   * Gerrit is "reasonably certain" that the returned email addresses actually
   * belong to the user of the account. Some emails may have been obtained from
   * the authentication provider, which in the case of OpenID may be trusting
   * the provider to have validated the address. Other emails may have been
   * validated by Gerrit directly.
   */
  public Set<String> getEmailAddresses() {
    final Set<String> emails = new HashSet<String>();
    for (final AccountExternalId e : externalIds) {
      if (e.getEmailAddress() != null && !e.getEmailAddress().isEmpty()) {
        emails.add(e.getEmailAddress());
      }
    }
    return emails;
  }

  /** The external identities that identify the account holder. */
  public Collection<AccountExternalId> getExternalIds() {
    return externalIds;
  }

  /** The set of groups maintained directly within the Gerrit database. */
  public Set<AccountGroup.Id> getInternalGroups() {
    return internalGroups;
  }
}
