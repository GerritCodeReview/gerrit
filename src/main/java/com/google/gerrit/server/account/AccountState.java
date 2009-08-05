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

import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.AccountGroup;

import java.util.Set;

public class AccountState {
  private final Account account;
  private final Set<AccountGroup.Id> actualGroups;
  private final Set<AccountGroup.Id> effectiveGroups;
  private final Set<String> emails;

  AccountState(final Account a, final Set<AccountGroup.Id> actual,
      final Set<AccountGroup.Id> effective, final Set<String> e) {
    this.account = a;
    this.actualGroups = actual;
    this.effectiveGroups = effective;
    this.emails = e;
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
    return emails;
  }

  /**
   * Get the set of groups the user has been declared a member of.
   * <p>
   * The returned set is the complete set of the user's groups. This can be a
   * superset of {@link #getEffectiveGroups()} if the user's account is not
   * sufficiently trusted to enable additional access.
   *
   * @return active groups for this user.
   */
  public Set<AccountGroup.Id> getActualGroups() {
    return actualGroups;
  }

  /**
   * Get the set of groups the user is currently a member of.
   * <p>
   * The returned set may be a subset of {@link #getActualGroups()}. If the
   * user's account is currently deemed to be untrusted then the effective group
   * set is only the anonymous and registered user groups. To enable additional
   * groups (and gain their granted permissions) the user must update their
   * account to use only trusted authentication providers.
   *
   * @return active groups for this user.
   */
  public Set<AccountGroup.Id> getEffectiveGroups() {
    return effectiveGroups;
  }
}
