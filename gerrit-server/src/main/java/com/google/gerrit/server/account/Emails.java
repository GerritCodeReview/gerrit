// Copyright (C) 2017 The Android Open Source Project
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

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Streams;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.gerrit.server.query.account.InternalAccountQuery;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;

/** Class to access accounts by email. */
@Singleton
public class Emails {
  private final ExternalIds externalIds;
  private final InternalAccountQuery accountQuery;

  @Inject
  public Emails(ExternalIds externalIds, InternalAccountQuery accountQuery) {
    this.externalIds = externalIds;
    this.accountQuery = accountQuery;
  }

  /**
   * Returns the accounts with the given email.
   *
   * <p>Each email should belong to a single account only. This means if more than one account is
   * returned there is an inconsistency in the external IDs.
   *
   * <p>The accounts are retrieved via the external ID cache. Each access to the external ID cache
   * requires reading the SHA1 of the refs/meta/external-ids branch. If accounts for multiple emails
   * are needed it is more efficient to use {@link #getAccountsFor(String...)} as this method reads
   * the SHA1 of the refs/meta/external-ids branch only once (and not once per email).
   *
   * <p>In addition accounts are included that have the given email as preferred email even if they
   * have no external ID for the preferred email. Having accounts with a preferred email that does
   * not exist as external ID is an inconsistency, but existing functionality relies on still
   * getting those accounts, which is why they are included. Accounts by preferred email are fetched
   * from the account index.
   *
   * @see #getAccountsFor(String...)
   */
  public ImmutableSet<Account.Id> getAccountFor(String email) throws IOException, OrmException {
    List<AccountState> byPreferredEmail = accountQuery.byPreferredEmail(email);
    return Streams.concat(
            externalIds.byEmail(email).stream().map(e -> e.accountId()),
            byPreferredEmail
                .stream()
                // the index query also matches prefixes and emails with other case,
                // filter those out
                .filter(a -> email.equals(a.getAccount().getPreferredEmail()))
                .map(a -> a.getAccount().getId()))
        .collect(toImmutableSet());
  }

  /**
   * Returns the accounts for the given emails.
   *
   * @see #getAccountFor(String)
   */
  public ImmutableSetMultimap<String, Account.Id> getAccountsFor(String... emails)
      throws IOException, OrmException {
    ImmutableSetMultimap.Builder<String, Account.Id> builder = ImmutableSetMultimap.builder();
    externalIds
        .byEmails(emails)
        .entries()
        .stream()
        .forEach(e -> builder.put(e.getKey(), e.getValue().accountId()));
    accountQuery
        .byPreferredEmail(emails)
        .entries()
        .stream()
        // the index query also matches prefixes and emails with other case,
        // filter those out
        .filter(e -> e.getKey().equals(e.getValue().getAccount().getPreferredEmail()))
        .forEach(e -> builder.put(e.getKey(), e.getValue().getAccount().getId()));
    return builder.build();
  }
}
