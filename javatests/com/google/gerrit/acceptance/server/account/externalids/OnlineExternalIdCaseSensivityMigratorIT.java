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

package com.google.gerrit.acceptance.server.account.externalids;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_GERRIT;
import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_USERNAME;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.util.concurrent.MoreExecutors;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.entities.Account;
import com.google.gerrit.server.account.externalids.DuplicateExternalIdKeyException;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIdFactory;
import com.google.gerrit.server.account.externalids.ExternalIdKeyFactory;
import com.google.gerrit.server.account.externalids.ExternalIdNotes;
import com.google.gerrit.server.account.externalids.OnlineExternalIdCaseSensivityMigratiorExecutor;
import com.google.gerrit.server.account.externalids.storage.notedb.ExternalIdCaseSensitivityMigrator;
import com.google.gerrit.server.account.externalids.storage.notedb.OnlineExternalIdCaseSensivityMigrator;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Repository;
import org.junit.Test;

public class OnlineExternalIdCaseSensivityMigratorIT extends AbstractDaemonTest {
  private Account.Id accountId = Account.id(66);
  private boolean isUserNameCaseInsensitive = false;

  @Inject private ExternalIdNotes.Factory externalIdNotesFactory;
  @Inject private ExternalIdKeyFactory externalIdKeyFactory;
  @Inject private ExternalIdFactory externalIdFactory;
  @Inject private OnlineExternalIdCaseSensivityMigrator objectUnderTest;

  @Override
  public Module createModule() {
    return new AbstractModule() {
      @Override
      protected void configure() {
        install(new FactoryModuleBuilder().build(ExternalIdCaseSensitivityMigrator.Factory.class));
        bind(ExecutorService.class)
            .annotatedWith(OnlineExternalIdCaseSensivityMigratiorExecutor.class)
            .toInstance(MoreExecutors.newDirectExecutorService());
      }
    };
  }

  @Test
  @GerritConfig(name = "auth.userNameCaseInsensitive", value = "true")
  @GerritConfig(name = "auth.userNameCaseInsensitiveMigrationMode", value = "true")
  public void shouldMigrateExternalId() throws IOException, ConfigInvalidException {
    try (Repository allUsersRepo = repoManager.openRepository(allUsers);
        MetaDataUpdate md = metaDataUpdateFactory.create(allUsers)) {
      ExternalIdNotes extIdNotes = externalIdNotesFactory.load(allUsersRepo);

      createExternalId(
          md, extIdNotes, SCHEME_GERRIT, "JonDoe", accountId, isUserNameCaseInsensitive);
      createExternalId(
          md, extIdNotes, SCHEME_USERNAME, "JonDoe", accountId, isUserNameCaseInsensitive);

      assertThat(getExactExternalId(extIdNotes, SCHEME_GERRIT, "JonDoe").isPresent()).isTrue();
      assertThat(getExactExternalId(extIdNotes, SCHEME_GERRIT, "jondoe").isPresent()).isFalse();

      assertThat(getExactExternalId(extIdNotes, SCHEME_USERNAME, "JonDoe").isPresent()).isTrue();
      assertThat(getExactExternalId(extIdNotes, SCHEME_USERNAME, "jondoe").isPresent()).isFalse();

      objectUnderTest.migrate();

      extIdNotes = externalIdNotesFactory.load(allUsersRepo);
      assertThat(getExactExternalId(extIdNotes, SCHEME_GERRIT, "JonDoe").isPresent()).isFalse();
      assertThat(getExactExternalId(extIdNotes, SCHEME_GERRIT, "jondoe").isPresent()).isTrue();

      assertThat(getExactExternalId(extIdNotes, SCHEME_USERNAME, "JonDoe").isPresent()).isFalse();
      assertThat(getExactExternalId(extIdNotes, SCHEME_USERNAME, "jondoe").isPresent()).isTrue();
    }
  }

  @Test
  @GerritConfig(name = "auth.userNameCaseInsensitive", value = "true")
  @GerritConfig(name = "auth.userNameCaseInsensitiveMigrationMode", value = "true")
  public void shouldNotThrowExceptionDuringTheMigrationForExternalIdsWithCaseInsensitiveSha1()
      throws IOException, ConfigInvalidException {

    final boolean caseInsensitiveUserName = true;

    try (Repository allUsersRepo = repoManager.openRepository(allUsers);
        MetaDataUpdate md = metaDataUpdateFactory.create(allUsers)) {
      ExternalIdNotes extIdNotes = externalIdNotesFactory.load(allUsersRepo);

      createExternalId(md, extIdNotes, SCHEME_GERRIT, "JonDoe", accountId, caseInsensitiveUserName);
      createExternalId(
          md, extIdNotes, SCHEME_USERNAME, "JonDoe", accountId, caseInsensitiveUserName);

      assertThat(getExactExternalId(extIdNotes, SCHEME_GERRIT, "JonDoe").isPresent()).isFalse();
      assertThat(getExactExternalId(extIdNotes, SCHEME_GERRIT, "jondoe").isPresent()).isTrue();

      assertThat(getExactExternalId(extIdNotes, SCHEME_USERNAME, "JonDoe").isPresent()).isFalse();
      assertThat(getExactExternalId(extIdNotes, SCHEME_USERNAME, "jondoe").isPresent()).isTrue();

      objectUnderTest.migrate();
    }
  }

  @Test
  @GerritConfig(name = "auth.userNameCaseInsensitive", value = "true")
  @GerritConfig(name = "auth.userNameCaseInsensitiveMigrationMode", value = "true")
  public void shouldNotCreateDuplicateExternaIdNotesWhenUpdatingAccount()
      throws IOException, ConfigInvalidException {
    try (Repository allUsersRepo = repoManager.openRepository(allUsers);
        MetaDataUpdate md = metaDataUpdateFactory.create(allUsers)) {
      ExternalIdNotes extIdNotes = externalIdNotesFactory.load(allUsersRepo);

      createExternalId(
          md, extIdNotes, SCHEME_GERRIT, "JonDoe", accountId, isUserNameCaseInsensitive);
      createExternalId(
          md, extIdNotes, SCHEME_USERNAME, "JonDoe", accountId, isUserNameCaseInsensitive);

      extIdNotes = externalIdNotesFactory.load(allUsersRepo);
      ExternalId extId =
          externalIdFactory.create(
              externalIdKeyFactory.create(SCHEME_USERNAME, "JonDoe", true),
              accountId,
              "test@email.com",
              "w1m9Bg85GQ4hijLNxW+6xAfj4r9wyk9rzVQelIHxuQ");
      extIdNotes.upsert(extId);
      extIdNotes.commit(md);

      extIdNotes = externalIdNotesFactory.load(allUsersRepo);

      assertThat(getExactExternalId(extIdNotes, SCHEME_GERRIT, "JonDoe").isPresent()).isTrue();
      assertThat(getExactExternalId(extIdNotes, SCHEME_GERRIT, "jondoe").isPresent()).isFalse();

      assertThat(getExactExternalId(extIdNotes, SCHEME_USERNAME, "JonDoe").isPresent()).isTrue();
      assertThat(getExactExternalId(extIdNotes, SCHEME_USERNAME, "jondoe").isPresent()).isFalse();

      assertThat(getExactExternalId(extIdNotes, SCHEME_USERNAME, "JonDoe").get().email())
          .isEqualTo("test@email.com");

      objectUnderTest.migrate();

      extIdNotes = externalIdNotesFactory.load(allUsersRepo);
      assertThat(getExactExternalId(extIdNotes, SCHEME_GERRIT, "JonDoe").isPresent()).isFalse();
      assertThat(getExactExternalId(extIdNotes, SCHEME_GERRIT, "jondoe").isPresent()).isTrue();

      assertThat(getExactExternalId(extIdNotes, SCHEME_USERNAME, "JonDoe").isPresent()).isFalse();
      assertThat(getExactExternalId(extIdNotes, SCHEME_USERNAME, "jondoe").isPresent()).isTrue();

      assertThat(getExactExternalId(extIdNotes, SCHEME_USERNAME, "jondoe").get().email())
          .isEqualTo("test@email.com");
    }
  }

  @Test
  @GerritConfig(name = "auth.userNameCaseInsensitive", value = "true")
  @GerritConfig(name = "auth.userNameCaseInsensitiveMigrationMode", value = "true")
  public void caseInsensitivityShouldWorkAfterMigration()
      throws IOException, ConfigInvalidException {

    try (Repository allUsersRepo = repoManager.openRepository(allUsers);
        MetaDataUpdate md = metaDataUpdateFactory.create(allUsers)) {
      ExternalIdNotes extIdNotes = externalIdNotesFactory.load(allUsersRepo);

      createExternalId(
          md, extIdNotes, SCHEME_USERNAME, "JonDoe", accountId, isUserNameCaseInsensitive);

      assertThat(getExactExternalId(extIdNotes, SCHEME_USERNAME, "JonDoe").isPresent()).isTrue();
      assertThat(getExactExternalId(extIdNotes, SCHEME_USERNAME, "jondoe").isPresent()).isFalse();

      objectUnderTest.migrate();

      extIdNotes = externalIdNotesFactory.load(allUsersRepo);
      assertThat(
              getExternalIdWithCaseInsensitive(extIdNotes, SCHEME_USERNAME, "JonDoe").isPresent())
          .isTrue();
      assertThat(getExactExternalId(extIdNotes, SCHEME_USERNAME, "jondoe").isPresent()).isTrue();
    }
  }

  @Test
  @GerritConfig(name = "auth.userNameCaseInsensitive", value = "true")
  @GerritConfig(name = "auth.userNameCaseInsensitiveMigrationMode", value = "true")
  public void shouldThrowExceptionWhenDuplicateKeys() throws IOException, ConfigInvalidException {

    try (Repository allUsersRepo = repoManager.openRepository(allUsers);
        MetaDataUpdate md = metaDataUpdateFactory.create(allUsers)) {
      ExternalIdNotes extIdNotes = externalIdNotesFactory.load(allUsersRepo);

      createExternalId(
          md, extIdNotes, SCHEME_USERNAME, "JonDoe", accountId, isUserNameCaseInsensitive);

      createExternalId(
          md, extIdNotes, SCHEME_USERNAME, "jondoe", Account.id(67), isUserNameCaseInsensitive);

      assertThat(getExactExternalId(extIdNotes, SCHEME_USERNAME, "JonDoe").isPresent()).isTrue();
      assertThat(getExactExternalId(extIdNotes, SCHEME_USERNAME, "jondoe").isPresent()).isTrue();

      assertThrows(DuplicateExternalIdKeyException.class, () -> objectUnderTest.migrate());
    }
  }

  @Test
  @GerritConfig(name = "auth.userNameCaseInsensitive", value = "false")
  @GerritConfig(name = "auth.userNameCaseInsensitiveMigrationMode", value = "true")
  public void shouldSkipMigrationWhenUserNameCaseInsensitiveIsSetToFalse()
      throws RepositoryNotFoundException, IOException, ConfigInvalidException {

    try (Repository allUsersRepo = repoManager.openRepository(allUsers);
        MetaDataUpdate md = metaDataUpdateFactory.create(allUsers)) {
      ExternalIdNotes extIdNotes = externalIdNotesFactory.load(allUsersRepo);

      createExternalId(
          md, extIdNotes, SCHEME_USERNAME, "JonDoe", accountId, isUserNameCaseInsensitive);

      assertThat(getExactExternalId(extIdNotes, SCHEME_USERNAME, "JonDoe").isPresent()).isTrue();
      assertThat(getExactExternalId(extIdNotes, SCHEME_USERNAME, "jondoe").isPresent()).isFalse();

      objectUnderTest.migrate();

      extIdNotes = externalIdNotesFactory.load(allUsersRepo);

      assertThat(getExactExternalId(extIdNotes, SCHEME_USERNAME, "JonDoe").isPresent()).isTrue();
      assertThat(getExactExternalId(extIdNotes, SCHEME_USERNAME, "jondoe").isPresent()).isFalse();
    }
  }

  @Test
  @GerritConfig(name = "auth.userNameCaseInsensitive", value = "true")
  @GerritConfig(name = "auth.userNameCaseInsensitiveMigrationMode", value = "false")
  public void shouldSkipMigrationWhenUserNameCaseInsensitiveMigrationModeIsSetToFalse()
      throws RepositoryNotFoundException, IOException, ConfigInvalidException {

    try (Repository allUsersRepo = repoManager.openRepository(allUsers);
        MetaDataUpdate md = metaDataUpdateFactory.create(allUsers)) {
      ExternalIdNotes extIdNotes = externalIdNotesFactory.load(allUsersRepo);

      createExternalId(
          md, extIdNotes, SCHEME_USERNAME, "JonDoe", accountId, isUserNameCaseInsensitive);

      objectUnderTest.migrate();

      extIdNotes = externalIdNotesFactory.load(allUsersRepo);
      assertThat(getExactExternalId(extIdNotes, SCHEME_USERNAME, "jondoe").isPresent()).isFalse();
    }
  }

  protected Optional<ExternalId> getExternalIdWithCaseInsensitive(
      ExternalIdNotes extIdNotes, String scheme, String id)
      throws IOException, ConfigInvalidException {
    return extIdNotes.get(externalIdKeyFactory.create(scheme, id, true));
  }

  protected Optional<ExternalId> getExactExternalId(
      ExternalIdNotes extIdNotes, String scheme, String id)
      throws IOException, ConfigInvalidException {
    return extIdNotes.get(externalIdKeyFactory.create(scheme, id, false));
  }

  protected void createExternalId(
      MetaDataUpdate md,
      ExternalIdNotes extIdNotes,
      String scheme,
      String id,
      Account.Id accountId,
      boolean isUserNameCaseInsensitive)
      throws IOException {
    ExternalId extId =
        externalIdFactory.create(
            externalIdKeyFactory.create(scheme, id, isUserNameCaseInsensitive), accountId);
    extIdNotes.insert(extId);
    extIdNotes.commit(md);
  }
}
