// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.query.account;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import com.google.common.base.Joiner;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.UsedAt;
import com.google.gerrit.entities.Project;
import com.google.gerrit.index.IndexConfig;
import com.google.gerrit.index.Schema;
import com.google.gerrit.index.SchemaFieldDefs.SchemaField;
import com.google.gerrit.index.query.InternalQuery;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIdKeyFactory;
import com.google.gerrit.server.config.AccountConfig;
import com.google.gerrit.server.index.account.AccountField;
import com.google.gerrit.server.index.account.AccountIndexCollection;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Query wrapper for the account index.
 *
 * <p>Instances are one-time-use. Other singleton classes should inject a Provider rather than
 * holding on to a single instance.
 */
public class InternalAccountQuery extends InternalQuery<AccountState, InternalAccountQuery> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final AccountConfig accountConfig;
  private final ExternalIdKeyFactory externalIdKeyFactory;

  @Inject
  InternalAccountQuery(
      AccountQueryProcessor queryProcessor,
      AccountIndexCollection indexes,
      IndexConfig indexConfig,
      ExternalIdKeyFactory externalIdKeyFactory,
      AccountConfig accountConfig) {

    super(queryProcessor, indexes, indexConfig);
    this.accountConfig = accountConfig;
    this.externalIdKeyFactory = externalIdKeyFactory;
  }

  public List<AccountState> byDefault(String query, boolean canSeeSecondaryEmails) {
    return query(AccountPredicates.defaultPredicate(schema(), canSeeSecondaryEmails, query));
  }

  public List<AccountState> byExternalId(String scheme, String id) {
    return byExternalId(externalIdKeyFactory.create(scheme, id));
  }

  public List<AccountState> byExternalId(ExternalId.Key externalId) {
    return query(AccountPredicates.externalIdIncludingSecondaryEmails(externalId.toString()));
  }

  @Nullable
  @UsedAt(UsedAt.Project.COLLABNET)
  public AccountState oneByExternalId(ExternalId.Key externalId) {
    List<AccountState> accountStates = byExternalId(externalId);
    if (accountStates.size() == 1) {
      return accountStates.get(0);
    } else if (!accountStates.isEmpty()) {
      StringBuilder msg = new StringBuilder();
      msg.append("Ambiguous external ID ").append(externalId).append(" for accounts: ");
      Joiner.on(", ")
          .appendTo(
              msg, accountStates.stream().map(a -> a.account().id().toString()).collect(toList()));
      logger.atWarning().log("%s", msg);
    }
    return null;
  }

  public List<AccountState> byFullName(String fullName) {
    return query(AccountPredicates.fullName(fullName));
  }

  /**
   * Queries for accounts that have a preferred email that matches the given email.
   *
   * <p>The local part of the email is compared either in a case-insensitive or case-sensitive
   * manner, depending on the configuration parameter {@code accounts.caseInsensitiveLocalPart}.
   * Check the configuration documentation for more details.
   *
   * @param email preferred email by which accounts should be found
   * @return list of accounts that have a preferred email that exactly matches the given email
   */
  public List<AccountState> byPreferredEmail(String email) {
    String normalizedEmail = normalizeEmailCase(email);
    if (hasPreferredEmailExact()) {
      return query(AccountPredicates.preferredEmailExact(normalizedEmail));
    }

    if (!hasPreferredEmail()) {
      return ImmutableList.of();
    }

    return query(AccountPredicates.preferredEmail(normalizedEmail)).stream()
        .filter(a -> a.account().preferredEmail().equals(normalizedEmail))
        .collect(toList());
  }

  /**
   * Makes multiple queries for accounts by preferred email.
   *
   * <p>The local part of the email is compared either in a case-insensitive or case-sensitive
   * manner, depending on the configuration parameter {@code accounts.caseInsensitiveLocalPart}.
   * Check the configuration documentation for more details.
   *
   * @param emails preferred emails by which accounts should be found
   * @return multimap of the given emails to accounts that have a preferred email that exactly
   *     matches this email
   */
  public Multimap<String, AccountState> byPreferredEmail(List<String> emails) {
    if (hasPreferredEmailExact()) {
      List<List<AccountState>> r =
          query(
              emails.stream()
                  .map(email -> AccountPredicates.preferredEmailExact(normalizeEmailCase(email)))
                  .collect(toList()));
      ListMultimap<String, AccountState> accountsByEmail = ArrayListMultimap.create();
      for (int i = 0; i < emails.size(); i++) {
        accountsByEmail.putAll(emails.get(i), r.get(i));
      }
      return accountsByEmail;
    }

    if (!hasPreferredEmail()) {
      return ImmutableListMultimap.of();
    }

    List<List<AccountState>> r =
        query(emails.stream().map(AccountPredicates::preferredEmail).collect(toList()));
    ListMultimap<String, AccountState> accountsByEmail = ArrayListMultimap.create();
    for (int i = 0; i < emails.size(); i++) {
      String email = emails.get(i);
      Set<AccountState> matchingAccounts =
          r.get(i).stream()
              .filter(a -> a.account().preferredEmail().equals(email))
              .collect(toSet());
      accountsByEmail.putAll(email, matchingAccounts);
    }
    return accountsByEmail;
  }

  public List<AccountState> byWatchedProject(Project.NameKey project) {
    return query(AccountPredicates.watchedProject(project));
  }

  private boolean hasField(SchemaField<AccountState, ?> field) {
    Schema<AccountState> s = schema();
    return (s != null && s.hasField(field));
  }

  private boolean hasPreferredEmail() {
    return hasField(AccountField.PREFERRED_EMAIL_LOWER_CASE_SPEC);
  }

  private boolean hasPreferredEmailExact() {
    return hasField(AccountField.PREFERRED_EMAIL_EXACT_SPEC);
  }

  private String normalizeEmailCase(String email) {
    return Arrays.asList(accountConfig.getCaseInsensitiveLocalParts())
            .contains(getLowerCaseEmailDomain(email))
        ? email.toLowerCase(Locale.US)
        : email;
  }

  private String getLowerCaseEmailDomain(String email) {
    String[] parts = email.split("@", 2);
    // The caller method byPreferredEmail can be invoked with the local part
    // of the email only. Handle this case by just returning it.
    if (parts.length != 2) {
      return email;
    }
    return parts[1].toLowerCase(Locale.US);
  }
}
