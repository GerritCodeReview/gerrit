// Copyright (C) 2025 The Android Open Source Project
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

package com.google.gerrit.acceptance.server.account;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.index.IndexConfig;
import com.google.gerrit.index.QueryOptions;
import com.google.gerrit.index.query.FieldBundle;
import com.google.gerrit.server.account.PeriodicAccountIndexer;
import com.google.gerrit.server.index.account.AccountField;
import com.google.gerrit.server.index.account.AccountIndex;
import com.google.gerrit.server.index.account.AccountIndexCollection;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Optional;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.junit.Test;

@UseLocalDisk
public class PeriodicAccountIndexerIT extends AbstractDaemonTest {

  @Inject private AccountIndexCollection indexes;
  @Inject private IndexConfig indexConfig;

  @Inject private PeriodicAccountIndexer periodicIndexer;

  private static final ImmutableSet<String> FIELDS =
      ImmutableSet.of(AccountField.ID_STR_FIELD_SPEC.getName());

  @Test
  public void removesNonExistingAccountsFromIndex() throws Exception {
    AccountInfo info = gApi.accounts().create("foo").get();
    Optional<Account.Id> accountId = Account.Id.tryParse(info._accountId.toString());
    assertThat(accountId).isPresent();

    AccountIndex i = indexes.getSearchIndex();
    Optional<FieldBundle> result =
        i.getRaw(accountId.get(), QueryOptions.create(indexConfig, 0, 1, FIELDS));
    assertThat(result).isPresent();

    // Delete the account by directly updating the All-Users repository.
    // Thus, Gerrit will not notice the deletion and will not remove the account
    // from the index
    deleteAccountRef(accountId.get());

    result = i.getRaw(accountId.get(), QueryOptions.create(indexConfig, 0, 1, FIELDS));
    assertThat(result).isPresent();

    periodicIndexer.run();

    result = i.getRaw(accountId.get(), QueryOptions.create(indexConfig, 0, 1, FIELDS));
    assertThat(result).isEmpty();
  }

  @Test
  public void reindexAccount() throws Exception {
    AccountInfo info = gApi.accounts().create("foo").get();
    Optional<Account.Id> accountId = Account.Id.tryParse(info._accountId.toString());
    assertThat(accountId).isPresent();

    AccountIndex i = indexes.getSearchIndex();

    // Simulate out of sync index by deleting an account
    i.delete(accountId.get());
    Optional<FieldBundle> result =
        i.getRaw(accountId.get(), QueryOptions.create(indexConfig, 0, 1, FIELDS));
    assertThat(result).isEmpty();

    periodicIndexer.run();

    result = i.getRaw(accountId.get(), QueryOptions.create(indexConfig, 0, 1, FIELDS));
    assertThat(result).isPresent();
  }

  private void deleteAccountRef(Account.Id accountId) throws IOException {
    String accountRef = RefNames.refsUsers(accountId);
    try (Repository repo = repoManager.openRepository(allUsers)) {
      RefUpdate ru = repo.updateRef(accountRef);
      ru.setForceUpdate(true);
      ru.delete();
    }
  }
}
