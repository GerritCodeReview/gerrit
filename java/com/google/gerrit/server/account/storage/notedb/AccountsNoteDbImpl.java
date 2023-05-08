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

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Account;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.metrics.Timer0;
import com.google.gerrit.server.account.AccountConfig;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.Accounts;
import com.google.gerrit.server.account.externalids.ExternalIds;
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
import org.eclipse.jgit.lib.Repository;

@Singleton
public class AccountsNoteDbImpl implements Accounts {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final GitRepositoryManager repoManager;

  private final AllUsersName allUsersName;

  private final ExternalIds externalIds;
  private final Timer0 readSingleLatency;

  @Inject
  AccountsNoteDbImpl(
      GitRepositoryManager repoManager,
      AllUsersName allUsersName,
      ExternalIds externalIds,
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

    return AccountState.fromAccountConfig(externalIds, cfg, defaultPreferences);
  }
}
