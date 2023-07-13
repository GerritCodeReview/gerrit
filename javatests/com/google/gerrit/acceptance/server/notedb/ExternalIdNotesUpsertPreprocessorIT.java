// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.acceptance.server.notedb;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.entities.Account;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.server.ServerInitiated;
import com.google.gerrit.server.account.AccountsUpdate;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIdFactory;
import com.google.gerrit.server.account.externalids.ExternalIdUpsertPreprocessor;
import com.google.gerrit.server.account.externalids.storage.notedb.ExternalIdNotes;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.notedb.Sequences;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jgit.lib.Repository;
import org.junit.Before;
import org.junit.Test;

/** Tests {@link ExternalIdUpsertPreprocessor}. */
@TestPlugin(
    name = "external-id-update-preprocessor",
    sysModule =
        "com.google.gerrit.acceptance.server.notedb.ExternalIdNotesUpsertPreprocessorIT$TestModule")
public class ExternalIdNotesUpsertPreprocessorIT extends LightweightPluginDaemonTest {
  @Inject private Sequences sequences;
  @Inject private @ServerInitiated Provider<AccountsUpdate> accountsUpdateProvider;
  @Inject private ExternalIdNotes.Factory extIdNotesFactory;
  @Inject private ExternalIdFactory extIdFactory;

  public static class TestModule extends AbstractModule {
    @Override
    protected void configure() {
      bind(ExternalIdUpsertPreprocessor.class)
          .annotatedWith(Exports.named("TestPreprocessor"))
          .to(TestPreprocessor.class);
    }
  }

  private TestPreprocessor testPreprocessor;

  @Before
  public void setUp() {
    testPreprocessor = plugin.getSysInjector().getInstance(TestPreprocessor.class);
    testPreprocessor.reset();
  }

  @Test
  public void insertAccount() throws Exception {
    Account.Id id = Account.id(sequences.nextAccountId());
    ExternalId extId = extIdFactory.create("foo", "bar", id);
    accountsUpdateProvider.get().insert("test", id, u -> u.addExternalId(extId));
    assertThat(testPreprocessor.upserted).containsExactly(extId);
  }

  @Test
  public void replaceByKeys() throws Exception {
    Account.Id id = Account.id(sequences.nextAccountId());
    ExternalId extId1 = extIdFactory.create("foo", "bar1", id);
    ExternalId extId2 = extIdFactory.create("foo", "bar2", id);
    accountsUpdateProvider.get().insert("test", id, u -> u.addExternalId(extId1));

    testPreprocessor.reset();
    try (Repository allUsersRepo = repoManager.openRepository(allUsers);
        MetaDataUpdate md = metaDataUpdateFactory.create(allUsers)) {
      ExternalIdNotes extIdNotes = extIdNotesFactory.load(allUsersRepo);
      extIdNotes.replaceByKeys(ImmutableSet.of(extId1.key()), ImmutableSet.of(extId2));
      extIdNotes.commit(md);
    }
    assertThat(testPreprocessor.upserted).containsExactly(extId2);
  }

  @Test
  public void insert() throws Exception {
    Account.Id id = Account.id(sequences.nextAccountId());
    ExternalId extId = extIdFactory.create("foo", "bar", id);

    try (Repository allUsersRepo = repoManager.openRepository(allUsers);
        MetaDataUpdate md = metaDataUpdateFactory.create(allUsers)) {
      ExternalIdNotes extIdNotes = extIdNotesFactory.load(allUsersRepo);
      extIdNotes.insert(extId);
      extIdNotes.commit(md);
    }
    assertThat(testPreprocessor.upserted).containsExactly(extId);
  }

  @Test
  public void upsert() throws Exception {
    Account.Id id = Account.id(sequences.nextAccountId());
    ExternalId extId = extIdFactory.create("foo", "bar", id);

    try (Repository allUsersRepo = repoManager.openRepository(allUsers);
        MetaDataUpdate md = metaDataUpdateFactory.create(allUsers)) {
      ExternalIdNotes extIdNotes = extIdNotesFactory.load(allUsersRepo);
      extIdNotes.upsert(extId);
      extIdNotes.commit(md);
    }
    assertThat(testPreprocessor.upserted).containsExactly(extId);
  }

  @Test
  public void replace() throws Exception {
    Account.Id id = Account.id(sequences.nextAccountId());
    ExternalId extId1 = extIdFactory.create("foo", "bar1", id);
    ExternalId extId2 = extIdFactory.create("foo", "bar2", id);
    accountsUpdateProvider.get().insert("test", id, u -> u.addExternalId(extId1));

    testPreprocessor.reset();
    try (Repository allUsersRepo = repoManager.openRepository(allUsers);
        MetaDataUpdate md = metaDataUpdateFactory.create(allUsers)) {
      ExternalIdNotes extIdNotes = extIdNotesFactory.load(allUsersRepo);
      extIdNotes.replace(ImmutableSet.of(extId1), ImmutableSet.of(extId2));
      extIdNotes.commit(md);
    }
    assertThat(testPreprocessor.upserted).containsExactly(extId2);
  }

  @Test
  public void replace_viaAccountsUpdate() throws Exception {
    Account.Id id = Account.id(sequences.nextAccountId());
    ExternalId extId1 = extIdFactory.create("foo", "bar", id, "email1@foo", "hash");
    ExternalId extId2 = extIdFactory.create("foo", "bar", id, "email2@foo", "hash");
    accountsUpdateProvider.get().insert("test", id, u -> u.addExternalId(extId1));

    testPreprocessor.reset();
    accountsUpdateProvider.get().update("test", id, u -> u.updateExternalId(extId2));
    assertThat(testPreprocessor.upserted).containsExactly(extId2);
  }

  @Test
  public void blockUpsert() throws Exception {
    Account.Id id = Account.id(sequences.nextAccountId());
    ExternalId extId = extIdFactory.create("foo", "bar", id);
    testPreprocessor.throwException = true;
    StorageException e =
        assertThrows(
            StorageException.class,
            () -> accountsUpdateProvider.get().insert("test", id, u -> u.addExternalId(extId)));
    assertThat(e).hasMessageThat().contains("upsert not good");
    assertThat(testPreprocessor.upserted).isEmpty();
  }

  @Test
  public void blockUpsert_replace() throws Exception {
    Account.Id id = Account.id(sequences.nextAccountId());
    ExternalId extId1 = extIdFactory.create("foo", "bar", id, "email1@foo", "hash");
    ExternalId extId2 = extIdFactory.create("foo", "bar", id, "email2@foo", "hash");
    accountsUpdateProvider.get().insert("test", id, u -> u.addExternalId(extId1));

    assertThat(accounts.get(id).get().externalIds()).containsExactly(extId1);

    testPreprocessor.reset();
    testPreprocessor.throwException = true;
    try (Repository allUsersRepo = repoManager.openRepository(allUsers);
        MetaDataUpdate md = metaDataUpdateFactory.create(allUsers)) {
      ExternalIdNotes extIdNotes = extIdNotesFactory.load(allUsersRepo);
      extIdNotes.replace(ImmutableSet.of(extId1), ImmutableSet.of(extId2));
      StorageException e = assertThrows(StorageException.class, () -> extIdNotes.commit(md));
      assertThat(e).hasMessageThat().contains("upsert not good");
    }
    assertThat(testPreprocessor.upserted).isEmpty();
    assertThat(accounts.get(id).get().externalIds()).containsExactly(extId1);
  }

  @Singleton
  public static class TestPreprocessor implements ExternalIdUpsertPreprocessor {
    List<ExternalId> upserted = new ArrayList<>();

    boolean throwException = false;

    @Override
    public void upsert(ExternalId extId) {
      assertThat(extId.blobId()).isNotNull();
      if (throwException) {
        throw new StorageException("upsert not good");
      }
      upserted.add(extId);
    }

    void reset() {
      upserted.clear();
      throwException = false;
    }
  }
}
