// Copyright (C) 2023 The Android Open Source Project
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

package com.google.gerrit.server.account.storage.notedb;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.NotifyConfig;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.metrics.Timer0;
import com.google.gerrit.server.account.AccountConfig;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.Accounts;
import com.google.gerrit.server.account.ProjectWatches;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.storage.notedb.ExternalIdNotes;
import com.google.gerrit.server.account.externalids.storage.notedb.ExternalIdsNoteDbImpl;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.CachedPreferences;
import com.google.gerrit.server.config.VersionedDefaultPreferences;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import javax.inject.Singleton;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;

@Singleton
public class AccountsNoteDbImpl implements Accounts {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final GitRepositoryManager repoManager;

  private final AllUsersName allUsersName;

  private final ExternalIdsNoteDbImpl externalIds;
  private final Timer0 readSingleLatency;

  @Inject
  AccountsNoteDbImpl(
      GitRepositoryManager repoManager,
      AllUsersName allUsersName,
      ExternalIdsNoteDbImpl externalIds,
      MetricMaker metricMaker) {
    this.repoManager = repoManager;
    this.allUsersName = allUsersName;
    this.externalIds = externalIds;
    this.readSingleLatency =
        metricMaker.newTimer(
            "notedb/read_single_account_config_latency",
            new Description("Latency for reading a single account config.")
                .setCumulative()
                .setUnit(Description.Units.MILLISECONDS));
  }

  @Override
  public Optional<AccountState> get(Account.Id accountId)
      throws IOException, ConfigInvalidException {
    try (Repository repo = repoManager.openRepository(allUsersName)) {
      return read(repo, accountId);
    }
  }

  @Override
  public List<AccountState> get(Collection<Account.Id> accountIds)
      throws IOException, ConfigInvalidException {
    List<AccountState> accounts = new ArrayList<>(accountIds.size());
    try (Repository repo = repoManager.openRepository(allUsersName)) {
      for (Account.Id accountId : accountIds) {
        read(repo, accountId).ifPresent(accounts::add);
      }
    }
    return accounts;
  }

  @Override
  public List<AccountState> all() throws IOException {
    Set<Account.Id> accountIds = allIds();
    List<AccountState> accounts = new ArrayList<>(accountIds.size());
    try (Repository repo = repoManager.openRepository(allUsersName)) {
      for (Account.Id accountId : accountIds) {
        try {
          read(repo, accountId).ifPresent(accounts::add);
        } catch (Exception e) {
          logger.atSevere().withCause(e).log("Ignoring invalid account %s", accountId);
        }
      }
    }
    return accounts;
  }

  @Override
  public Set<Account.Id> allIds() throws IOException {
    return readUserRefs().collect(toSet());
  }

  @Override
  public List<Account.Id> firstNIds(int n) throws IOException {
    return readUserRefs().sorted(comparing(Account.Id::get)).limit(n).collect(toList());
  }

  @Override
  public boolean hasAnyAccount() throws IOException {
    try (Repository repo = repoManager.openRepository(allUsersName)) {
      return AccountsNoteDbRepoReader.hasAnyAccount(repo);
    }
  }

  /**
   * Creates an AccountState from the given account config.
   *
   * @param externalIds class to access external IDs
   * @param accountConfig the account config, must already be loaded
   * @param defaultPreferences the default preferences for this Gerrit installation
   * @return the account state, {@link Optional#empty()} if the account doesn't exist
   * @throws IOException if accessing the external IDs fails
   */
  static Optional<AccountState> getFromAccountConfig(
      ExternalIdsNoteDbImpl externalIds,
      AccountConfig accountConfig,
      CachedPreferences defaultPreferences)
      throws IOException {
    return getFromAccountConfig(externalIds, accountConfig, null, defaultPreferences);
  }

  /**
   * Creates an AccountState from the given account config.
   *
   * <p>If external ID notes are provided the revision of the external IDs branch from which the
   * external IDs for the account should be loaded is taken from the external ID notes. If external
   * ID notes are not given the revision of the external IDs branch is taken from the account
   * config. Updating external IDs is done via {@link ExternalIdNotes} and if external IDs were
   * updated the revision of the external IDs branch in account config is outdated. Hence after
   * updating external IDs the external ID notes must be provided.
   *
   * @param externalIds class to access external IDs
   * @param accountConfig the account config, must already be loaded
   * @param extIdNotes external ID notes, must already be loaded, may be {@code null}
   * @param defaultPreferences the default preferences for this Gerrit installation
   * @return the account state, {@link Optional#empty()} if the account doesn't exist
   * @throws IOException if accessing the external IDs fails
   */
  static Optional<AccountState> getFromAccountConfig(
      ExternalIdsNoteDbImpl externalIds,
      AccountConfig accountConfig,
      @Nullable ExternalIdNotes extIdNotes,
      CachedPreferences defaultPreferences)
      throws IOException {
    if (!accountConfig.getLoadedAccount().isPresent()) {
      return Optional.empty();
    }
    Account account = accountConfig.getLoadedAccount().get();

    Optional<ObjectId> extIdsRev =
        extIdNotes != null
            ? Optional.ofNullable(extIdNotes.getRevision())
            : accountConfig.getExternalIdsRev();
    ImmutableSet<ExternalId> extIds =
        extIdsRev.isPresent()
            ? externalIds.byAccount(account.id(), extIdsRev.get())
            : ImmutableSet.of();

    // Don't leak references to AccountConfig into the AccountState, since it holds a reference to
    // an open Repository instance.
    ImmutableMap<ProjectWatches.ProjectWatchKey, ImmutableSet<NotifyConfig.NotifyType>>
        projectWatches = accountConfig.getProjectWatches();

    return Optional.of(
        AccountState.withState(
            account,
            extIds,
            ExternalId.getUserName(extIds),
            projectWatches,
            Optional.of(defaultPreferences),
            Optional.of(accountConfig.asCachedPreferences())));
  }

  private Stream<Account.Id> readUserRefs() throws IOException {
    try (Repository repo = repoManager.openRepository(allUsersName)) {
      return AccountsNoteDbRepoReader.readAllIds(repo);
    }
  }

  private Optional<AccountState> read(Repository allUsersRepository, Account.Id accountId)
      throws IOException, ConfigInvalidException {
    AccountConfig cfg;
    CachedPreferences defaultPreferences;
    try (Timer0.Context ignored = readSingleLatency.start()) {
      cfg = new AccountConfig(accountId, allUsersName, allUsersRepository).load();
      defaultPreferences =
          CachedPreferences.fromConfig(
              VersionedDefaultPreferences.get(allUsersRepository, allUsersName));
    }

    return getFromAccountConfig(externalIds, cfg, defaultPreferences);
  }
}
