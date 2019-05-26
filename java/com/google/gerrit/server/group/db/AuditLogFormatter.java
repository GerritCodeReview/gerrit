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

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AccountGroup;
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
  @Nullable private final String serverId;

  public static AuditLogFormatter createBackedBy(
      AccountCache accountCache, GroupBackend groupBackend, String serverId) {
    return create(
        accountId -> getAccount(accountCache, accountId),
        groupUuid -> getGroup(groupBackend, groupUuid),
        serverId);
  }

  private static Optional<Account> getAccount(AccountCache accountCache, Account.Id accountId) {
    return accountCache.get(accountId).map(AccountState::account);
  }

  private static Optional<GroupDescription.Basic> getGroup(
      GroupBackend groupBackend, AccountGroup.UUID groupUuid) {
    return Optional.ofNullable(groupBackend.get(groupUuid));
  }

  public static AuditLogFormatter createBackedBy(
      ImmutableSet<Account> allAccounts,
      ImmutableSet<GroupDescription.Basic> allGroups,
      String serverId) {
    return create(id -> getAccount(allAccounts, id), uuid -> getGroup(allGroups, uuid), serverId);
  }

  private static Optional<GroupDescription.Basic> getGroup(
      ImmutableSet<GroupDescription.Basic> groups, AccountGroup.UUID uuid) {
    return groups.stream().filter(group -> group.getGroupUUID().equals(uuid)).findAny();
  }

  private static Optional<Account> getAccount(ImmutableSet<Account> accounts, Account.Id id) {
    return accounts.stream().filter(account -> account.id().equals(id)).findAny();
  }

  public static AuditLogFormatter createPartiallyWorkingFallBack() {
    return new AuditLogFormatter(id -> Optional.empty(), uuid -> Optional.empty());
  }

  public static AuditLogFormatter create(
      Function<Account.Id, Optional<Account>> accountRetriever,
      Function<AccountGroup.UUID, Optional<GroupDescription.Basic>> groupRetriever,
      String serverId) {
    return new AuditLogFormatter(accountRetriever, groupRetriever, serverId);
  }

  private AuditLogFormatter(
      Function<Account.Id, Optional<Account>> accountRetriever,
      Function<AccountGroup.UUID, Optional<GroupDescription.Basic>> groupRetriever,
      String serverId) {
    this.accountRetriever = requireNonNull(accountRetriever);
    this.groupRetriever = requireNonNull(groupRetriever);
    this.serverId = requireNonNull(serverId);
  }

  private AuditLogFormatter(
      Function<Account.Id, Optional<Account>> accountRetriever,
      Function<AccountGroup.UUID, Optional<GroupDescription.Basic>> groupRetriever) {
    this.accountRetriever = requireNonNull(accountRetriever);
    this.groupRetriever = requireNonNull(groupRetriever);
    serverId = null;
  }

  /**
   * Creates a parsable {@code PersonIdent} for commits which are used as an audit log.
   *
   * <p><em>Parsable</em> means that we can unambiguously identify the original account when being
   * presented with a {@code PersonIdent} of a commit.
   *
   * <p>We typically use the initiator of an action as the author of the commit when using those
   * commits as an audit log. That's something which has to be specified by a caller of this method
   * as this class doesn't create any commits itself.
   *
   * @param account the {@code Account} of the user who should be represented
   * @param personIdent a {@code PersonIdent} which provides the timestamp for the created {@code
   *     PersonIdent}
   * @return a {@code PersonIdent} which can be used for the author of a commit
   */
  public PersonIdent getParsableAuthorIdent(Account account, PersonIdent personIdent) {
    return getParsableAuthorIdent(account.getName(), account.id(), personIdent);
  }

  /**
   * Creates a parsable {@code PersonIdent} for commits which are used as an audit log.
   *
   * <p>See {@link #getParsableAuthorIdent(Account, PersonIdent)} for further details.
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
    // If we ever switch to UUIDs for accounts, consider to remove the serverId and to use a similar
    // approach as for group UUIDs.
    checkState(
        serverId != null, "serverId must be defined; fall-back AuditLogFormatter isn't sufficient");
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
