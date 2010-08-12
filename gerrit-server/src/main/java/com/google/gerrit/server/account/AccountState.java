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

import static com.google.gerrit.reviewdb.AccountExternalId.SCHEME_USERNAME;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountExternalId;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gwtorm.client.Column;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AccountState {
  /** Convert from an AccountState to an Account. */
  public static final Function<AccountState, Account> GET_ACCOUNT =
      new Function<AccountState, Account>() {
        @Override
        public Account apply(AccountState in) {
          return in.getAccount();
        }
      };

  @Column(id = 1)
  protected Account account;

  @Column(id = 2)
  protected Set<AccountGroup.Id> internalGroups;

  @Column(id = 3)
  protected Collection<AccountExternalId> externalIds;

  protected AccountState() {
  }

  public AccountState(final Account account,
      final Set<AccountGroup.Id> actualGroups,
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

  /** The external identities that match a particular email address. */
  public Collection<AccountExternalId> getExternalIds(String emailAddress) {
    List<AccountExternalId> r = Lists.newArrayListWithCapacity(externalIds.size());
    for (AccountExternalId extId : externalIds) {
      String accEmail = extId.getEmailAddress();
      if (accEmail != null && accEmail.equals(emailAddress)) {
        r.add(extId);
      }
    }
    return r;
  }

  /** The set of groups maintained directly within the Gerrit database. */
  public Set<AccountGroup.Id> getInternalGroups() {
    return internalGroups;
  }

  private static String getUserName(Collection<AccountExternalId> ids) {
    for (AccountExternalId id : ids) {
      if (id.isScheme(SCHEME_USERNAME)) {
        return id.getSchemeRest();
      }
    }
    return null;
  }
}
