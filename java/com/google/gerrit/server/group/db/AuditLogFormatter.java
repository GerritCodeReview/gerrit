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

package com.google.gerrit.server.group.db;

import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.GroupBackend;
import java.util.Optional;
import java.util.function.Function;
import org.eclipse.jgit.lib.PersonIdent;

/**
 * A formatter for entities used in an audit log which is typically represented by NoteDb commits.
 *
 * <p>The formatted representation of those entities must be parsable so that we can read them later
 * on and map them back to their original entities. {@link AuditLogFormatter} and {@link
 * com.google.gerrit.server.notedb.NoteDbUtil NoteDbUtil} contain some of those parsing/mapping
 * methods.
 */
public class AuditLogFormatter {
  private final Function<Account.Id, Optional<Account>> accountRetriever;
  private final Function<AccountGroup.UUID, Optional<GroupDescription.Basic>> groupRetriever;
  private final String serverId;

  public AuditLogFormatter(AccountCache accountCache, GroupBackend groupBackend, String serverId) {
    this(
        accountId -> getAccount(accountCache, accountId),
        groupUuid -> getGroup(groupBackend, groupUuid),
        serverId);
  }

  public AuditLogFormatter(
      Function<Account.Id, Optional<Account>> accountRetriever,
      Function<AccountGroup.UUID, Optional<GroupDescription.Basic>> groupRetriever,
      String serverId) {
    this.accountRetriever = accountRetriever;
    this.groupRetriever = groupRetriever;
    this.serverId = serverId;
  }

  private static Optional<Account> getAccount(AccountCache accountCache, Account.Id accountId) {
    AccountState accountState = accountCache.getOrNull(accountId);
    return Optional.ofNullable(accountState).map(AccountState::getAccount);
  }

  private static Optional<GroupDescription.Basic> getGroup(
      GroupBackend groupBackend, AccountGroup.UUID groupUuid) {
    return Optional.ofNullable(groupBackend.get(groupUuid));
  }

  /**
   * Creates a parsable {@code PersonIdent} for commits which are used as an audit log.
   *
   * <p>We typically use the initiator of an action as the author of the commit.
   *
   * @param account the {@code Account} of the user who should be represented
   * @param personIdent a {@code PersonIdent} which provides the timestamp for the created {@code
   *     PersonIdent}
   * @return a {@code PersonIdent} which can be used for the author of a commit
   */
  public PersonIdent getParsableAuthorIdent(Account account, PersonIdent personIdent) {
    return getParsableAuthorIdent(account.getName(), account.getId(), personIdent);
  }

  /**
   * Creates a parsable {@code PersonIdent} for commits which are used as an audit log.
   *
   * <p>We typically use the initiator of an action as the author of the commit.
   *
   * @param accountId the ID of the account of the user who should be represented
   * @param personIdent a {@code PersonIdent} which provides the timestamp for the created {@code
   *     PersonIdent}
   * @return a {@code PersonIdent} which can be used for the author of a commit
   */
  public PersonIdent getParsableAuthorIdent(Account.Id accountId, PersonIdent personIdent) {
    String accountName = getAccountName(accountId);
    return getParsableAuthorIdent(accountName, accountId, personIdent);
  }

  /**
   * Provides a parsable representation of an account for use in e.g. commit messages.
   *
   * @param accountId the ID of the account of the user who should be represented
   * @return the {@code String} representation of the account
   */
  public String getParsableAccount(Account.Id accountId) {
    String accountName = getAccountName(accountId);
    return formatNameEmail(accountName, getEmailForAuditLog(accountId));
  }

  /**
   * Provides a parsable representation of a group for use in e.g. commit messages.
   *
   * @param groupUuid the UUID of the group
   * @return the {@code String} representation of the group
   */
  public String getParsableGroup(AccountGroup.UUID groupUuid) {
    String uuid = groupUuid.get();
    Optional<GroupDescription.Basic> group = groupRetriever.apply(groupUuid);
    String name = group.map(GroupDescription.Basic::getName).orElse(uuid);
    return formatNameEmail(name, uuid);
  }

  private String getAccountName(Account.Id accountId) {
    Optional<Account> account = accountRetriever.apply(accountId);
    return account
        .map(Account::getName)
        // Historically, the database did not enforce relational integrity, so it is
        // possible for groups to have non-existing members.
        .orElse("No Account for Id #" + accountId);
  }

  private PersonIdent getParsableAuthorIdent(
      String accountname, Account.Id accountId, PersonIdent personIdent) {
    return new PersonIdent(
        accountname,
        getEmailForAuditLog(accountId),
        personIdent.getWhen(),
        personIdent.getTimeZone());
  }

  private String getEmailForAuditLog(Account.Id accountId) {
    return accountId.get() + "@" + serverId;
  }

  private static String formatNameEmail(String name, String email) {
    StringBuilder formattedResult = new StringBuilder();
    PersonIdent.appendSanitized(formattedResult, name);
    formattedResult.append(" <");
    PersonIdent.appendSanitized(formattedResult, email);
    formattedResult.append(">");
    return formattedResult.toString();
  }
}
