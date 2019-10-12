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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SetMultimap;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.UserIdentity;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.gerrit.server.query.account.InternalAccountQuery;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.update.RetryHelper.Action;
import com.google.gerrit.server.update.RetryHelper.ActionType;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.eclipse.jgit.lib.PersonIdent;

/** Class to access accounts by email. */
@Singleton
public class Emails {
  private final ExternalIds externalIds;
  private final Provider<InternalAccountQuery> queryProvider;
  private final RetryHelper retryHelper;

  @Inject
  public Emails(
      ExternalIds externalIds,
      Provider<InternalAccountQuery> queryProvider,
      RetryHelper retryHelper) {
    this.externalIds = externalIds;
    this.queryProvider = queryProvider;
    this.retryHelper = retryHelper;
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
   * from the account index as a fallback for email addresses that could not be resolved using
   * {@link ExternalIds}.
   *
   * @see #getAccountsFor(String...)
   */
  public ImmutableSet<Account.Id> getAccountFor(String email) throws IOException {
    ImmutableSet<Account.Id> accounts =
        externalIds.byEmail(email).stream().map(ExternalId::accountId).collect(toImmutableSet());
    if (!accounts.isEmpty()) {
      return accounts;
    }

    return executeIndexQuery(() -> queryProvider.get().byPreferredEmail(email).stream())
        .map(a -> a.account().id())
        .collect(toImmutableSet());
  }

  /**
   * Returns the accounts for the given emails.
   *
   * @see #getAccountFor(String)
   */
  public ImmutableSetMultimap<String, Account.Id> getAccountsFor(String... emails)
      throws IOException {
    SetMultimap<String, Account.Id> result =
        MultimapBuilder.hashKeys(emails.length).hashSetValues(1).build();
    externalIds.byEmails(emails).entries().stream()
        .forEach(e -> result.put(e.getKey(), e.getValue().accountId()));
    List<String> emailsToBackfill =
        Arrays.stream(emails).filter(e -> !result.containsKey(e)).collect(toImmutableList());
    if (!emailsToBackfill.isEmpty()) {
      executeIndexQuery(
              () -> queryProvider.get().byPreferredEmail(emailsToBackfill).entries().stream())
          .forEach(e -> result.put(e.getKey(), e.getValue().account().id()));
    }
    return ImmutableSetMultimap.copyOf(result);
  }

  /**
   * Returns the accounts with the given email.
   *
   * <p>This method behaves just like {@link #getAccountFor(String)}, except that accounts are not
   * looked up by their preferred email. Thus, this method does not rely on the accounts index.
   */
  public ImmutableSet<Account.Id> getAccountForExternal(String email) throws IOException {
    return externalIds.byEmail(email).stream().map(ExternalId::accountId).collect(toImmutableSet());
  }

  public UserIdentity toUserIdentity(PersonIdent who) throws IOException {
    UserIdentity u = new UserIdentity();
    u.setName(who.getName());
    u.setEmail(who.getEmailAddress());
    u.setDate(new Timestamp(who.getWhen().getTime()));
    u.setTimeZone(who.getTimeZoneOffset());

    // If only one account has access to this email address, select it
    // as the identity of the user.
    //
    Set<Account.Id> a = getAccountFor(u.getEmail());
    if (a.size() == 1) {
      u.setAccount(a.iterator().next());
    }

    return u;
  }

  private <T> T executeIndexQuery(Action<T> action) {
    try {
      return retryHelper.execute(
          ActionType.INDEX_QUERY, action, StorageException.class::isInstance);
    } catch (Exception e) {
      Throwables.throwIfUnchecked(e);
      throw new StorageException(e);
    }
  }
}
