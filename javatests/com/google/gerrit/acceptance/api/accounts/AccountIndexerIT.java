// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.acceptance.api.accounts;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.gerrit.config.AllUsersName;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountConfig;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.InternalAccountUpdate;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.index.account.AccountIndexer;
import com.google.gerrit.server.query.account.InternalAccountQuery;
import com.google.gerrit.testing.InMemoryTestEnvironment;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.List;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.junit.Rule;
import org.junit.Test;

public class AccountIndexerIT {
  @Rule public InMemoryTestEnvironment testEnvironment = new InMemoryTestEnvironment();

  @Inject private AccountIndexer accountIndexer;
  @Inject private GerritApi gApi;
  @Inject private AccountCache accountCache;
  @Inject private Provider<InternalAccountQuery> accountQueryProvider;
  @Inject private GitRepositoryManager repoManager;
  @Inject private AllUsersName allUsersName;
  @Inject @GerritPersonIdent protected Provider<PersonIdent> serverIdent;

  @Test
  public void indexingUpdatesTheIndex() throws Exception {
    Account.Id accountId = createAccount("foo");
    String preferredEmail = "foo@example.com";
    updateAccountWithoutCacheOrIndex(
        accountId, newAccountUpdate().setPreferredEmail(preferredEmail).build());
    assertThat(accountQueryProvider.get().byPreferredEmail(preferredEmail)).isEmpty();

    accountIndexer.index(accountId);
    List<AccountState> matchedAccountStates =
        accountQueryProvider.get().byPreferredEmail(preferredEmail);
    assertThat(matchedAccountStates).hasSize(1);
    assertThat(matchedAccountStates.get(0).getAccount().getId()).isEqualTo(accountId);
  }

  @Test
  public void indexCannotBeCorruptedByStaleCache() throws Exception {
    Account.Id accountId = createAccount("foo");
    loadAccountToCache(accountId);
    String preferredEmail = "foo@example.com";
    updateAccountWithoutCacheOrIndex(
        accountId, newAccountUpdate().setPreferredEmail(preferredEmail).build());
    assertThat(accountQueryProvider.get().byPreferredEmail(preferredEmail)).isEmpty();

    accountIndexer.index(accountId);
    List<AccountState> matchedAccountStates =
        accountQueryProvider.get().byPreferredEmail(preferredEmail);
    assertThat(matchedAccountStates).hasSize(1);
    assertThat(matchedAccountStates.get(0).getAccount().getId()).isEqualTo(accountId);
  }

  @Test
  public void indexingUpdatesStaleCache() throws Exception {
    Account.Id accountId = createAccount("foo");
    loadAccountToCache(accountId);
    String status = "ooo";
    updateAccountWithoutCacheOrIndex(accountId, newAccountUpdate().setStatus(status).build());
    assertThat(accountCache.get(accountId).get().getAccount().getStatus()).isNull();

    accountIndexer.index(accountId);
    assertThat(accountCache.get(accountId).get().getAccount().getStatus()).isEqualTo(status);
  }

  @Test
  public void reindexingStaleAccountUpdatesTheIndex() throws Exception {
    Account.Id accountId = createAccount("foo");
    String preferredEmail = "foo@example.com";
    updateAccountWithoutCacheOrIndex(
        accountId, newAccountUpdate().setPreferredEmail(preferredEmail).build());
    assertThat(accountQueryProvider.get().byPreferredEmail(preferredEmail)).isEmpty();

    accountIndexer.reindexIfStale(accountId);
    List<AccountState> matchedAccountStates =
        accountQueryProvider.get().byPreferredEmail(preferredEmail);
    assertThat(matchedAccountStates).hasSize(1);
    assertThat(matchedAccountStates.get(0).getAccount().getId()).isEqualTo(accountId);
  }

  @Test
  public void notStaleAccountIsNotReindexed() throws Exception {
    Account.Id accountId = createAccount("foo");
    updateAccountWithoutCacheOrIndex(
        accountId, newAccountUpdate().setPreferredEmail("foo@example.com").build());
    accountIndexer.index(accountId);

    boolean reindexed = accountIndexer.reindexIfStale(accountId);
    assertWithMessage("Account should not have been reindexed").that(reindexed).isFalse();
  }

  @Test
  public void indexStalenessIsNotDerivedFromCacheStaleness() throws Exception {
    Account.Id accountId = createAccount("foo");
    updateAccountWithoutCacheOrIndex(
        accountId, newAccountUpdate().setPreferredEmail("foo@example.com").build());
    reloadAccountToCache(accountId);

    boolean reindexed = accountIndexer.reindexIfStale(accountId);
    assertWithMessage("Account should have been reindexed").that(reindexed).isTrue();
  }

  private Account.Id createAccount(String name) throws RestApiException {
    AccountInfo account = gApi.accounts().create(name).get();
    return new Account.Id(account._accountId);
  }

  private void reloadAccountToCache(Account.Id accountId) {
    accountCache.evict(accountId);
    loadAccountToCache(accountId);
  }

  private void loadAccountToCache(Account.Id accountId) {
    accountCache.get(accountId);
  }

  private static InternalAccountUpdate.Builder newAccountUpdate() {
    return InternalAccountUpdate.builder();
  }

  private void updateAccountWithoutCacheOrIndex(
      Account.Id accountId, InternalAccountUpdate accountUpdate)
      throws IOException, ConfigInvalidException {
    try (Repository allUsersRepo = repoManager.openRepository(allUsersName);
        MetaDataUpdate md =
            new MetaDataUpdate(GitReferenceUpdated.DISABLED, allUsersName, allUsersRepo)) {
      PersonIdent ident = serverIdent.get();
      md.getCommitBuilder().setAuthor(ident);
      md.getCommitBuilder().setCommitter(ident);

      AccountConfig accountConfig = new AccountConfig(accountId, allUsersRepo).load();
      accountConfig.setAccountUpdate(accountUpdate);
      accountConfig.commit(md);
    }
  }
}
