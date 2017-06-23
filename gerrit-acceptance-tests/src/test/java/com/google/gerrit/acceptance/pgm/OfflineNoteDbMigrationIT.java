// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.acceptance.pgm;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.AccountCreator;
import com.google.gerrit.acceptance.GerritServer;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.common.ChangeInput;
import com.google.gerrit.launcher.GerritLauncher;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.index.GerritIndexStatus;
import com.google.gerrit.server.index.change.ChangeSchemaDefinitions;
import com.google.gerrit.server.notedb.ConfigNotesMigration;
import com.google.gerrit.server.notedb.NoteDbChangeState;
import com.google.gerrit.server.notedb.NoteDbChangeState.PrimaryStorage;
import com.google.gerrit.server.notedb.NoteDbChangeState.RefState;
import com.google.gerrit.server.notedb.NotesMigrationState;
import com.google.gerrit.server.schema.ReviewDbFactory;
import com.google.gerrit.server.util.ManualRequestContext;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.gerrit.testutil.ConfigSuite;
import com.google.gerrit.testutil.TempFileUtil;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

/**
 * Tests for offline {@code migrate-to-note-db} program.
 *
 * <p><strong>Note:</strong> These tests are very slow due to the repeated daemon startup. Prefer
 * adding tests to {@link com.google.gerrit.acceptance.server.notedb.OnlineNoteDbMigrationIT} if
 * possible.
 */
@UseLocalDisk
@NoHttpd
public class OfflineNoteDbMigrationIT {
  @Rule
  public TestWatcher testWatcher =
      new TestWatcher() {
        @Override
        protected void starting(Description description) {
          serverDesc = GerritServer.Description.forTestMethod(description, ConfigSuite.DEFAULT);
        }
      };

  private GerritServer.Description serverDesc;

  private Path site;
  private StoredConfig gerritConfig;

  private Account.Id accountId;
  private Project.NameKey project;
  private Change.Id changeId;

  @Before
  public void setUp() throws Exception {
    site = TempFileUtil.createTempDirectory().toPath();
    GerritServer.init(serverDesc, new Config(), site);
    gerritConfig = new FileBasedConfig(new SitePaths(site).gerrit_config.toFile(), FS.detect());
  }

  @After
  public void tearDown() throws Exception {
    TempFileUtil.cleanup();
  }

  @Test
  public void rebuildOneChangeTrialMode() throws Exception {
    assertNotesMigrationState(NotesMigrationState.REVIEW_DB);
    setUpOneChange();

    migrate();
    assertNotesMigrationState(NotesMigrationState.READ_WRITE_NO_SEQUENCE);

    try (GerritServer server = startServer();
        ManualRequestContext ctx = openContext(server, accountId)) {
      GitRepositoryManager repoManager =
          server.getTestInjector().getInstance(GitRepositoryManager.class);
      ObjectId metaId;
      try (Repository repo = repoManager.openRepository(project)) {
        Ref ref = repo.exactRef(RefNames.changeMetaRef(changeId));
        assertThat(ref).isNotNull();
        metaId = ref.getObjectId();
      }

      try (ReviewDb db = openUnderlyingReviewDb(server)) {
        Change c = db.changes().get(changeId);
        assertThat(c).isNotNull();
        NoteDbChangeState state = NoteDbChangeState.parse(c);
        assertThat(state).isNotNull();
        assertThat(state.getPrimaryStorage()).isEqualTo(PrimaryStorage.REVIEW_DB);
        assertThat(state.getRefState()).hasValue(RefState.create(metaId, ImmutableMap.of()));
      }
    }
  }

  @Test
  public void migrateOneChange() throws Exception {
    assertNotesMigrationState(NotesMigrationState.REVIEW_DB);
    setUpOneChange();

    migrate("--trial", "false");
    assertNotesMigrationState(NotesMigrationState.NOTE_DB_UNFUSED);

    try (GerritServer server = startServer();
        ManualRequestContext ctx = openContext(server, accountId)) {
      GitRepositoryManager repoManager =
          server.getTestInjector().getInstance(GitRepositoryManager.class);
      try (Repository repo = repoManager.openRepository(project)) {
        assertThat(repo.exactRef(RefNames.changeMetaRef(changeId))).isNotNull();
      }

      try (ReviewDb db = openUnderlyingReviewDb(server)) {
        Change c = db.changes().get(changeId);
        assertThat(c).isNotNull();
        NoteDbChangeState state = NoteDbChangeState.parse(c);
        assertThat(state).isNotNull();
        assertThat(state.getPrimaryStorage()).isEqualTo(PrimaryStorage.NOTE_DB);
        assertThat(state.getRefState()).isEmpty();

        ChangeInput in = new ChangeInput(project.get(), "master", "NoteDb-only change");
        in.newBranch = true;
        GerritApi gApi = server.getTestInjector().getInstance(GerritApi.class);
        Change.Id id2 = new Change.Id(gApi.changes().create(in).info()._number);
        assertThat(db.changes().get(id2)).isNull();
      }
    }
  }

  @Test
  public void migrationDoesNotRequireIndex() throws Exception {
    assertNotesMigrationState(NotesMigrationState.REVIEW_DB);
    setUpOneChange();

    int version = ChangeSchemaDefinitions.INSTANCE.getLatest().getVersion();
    GerritIndexStatus status = new GerritIndexStatus(new SitePaths(site));
    assertThat(status.getReady(ChangeSchemaDefinitions.NAME, version)).isTrue();
    status.setReady(ChangeSchemaDefinitions.NAME, version, false);
    status.save();

    migrate("--trial", "false");
    assertNotesMigrationState(NotesMigrationState.NOTE_DB_UNFUSED);

    status = new GerritIndexStatus(new SitePaths(site));
    assertThat(status.getReady(ChangeSchemaDefinitions.NAME, version)).isFalse();

    // TODO(dborowitz): Remove when offline migration includes reindex.
    try (GerritServer server = startServer()) {
      fail("expected server startup to fail");
    } catch (GerritServer.StartupException e) {
      // Expected.
    }
  }

  private void setUpOneChange() throws Exception {
    project = new Project.NameKey("project");
    try (GerritServer server = startServer();
        ManualRequestContext ctx = openContext(server)) {
      accountId = ctx.getUser().getAccountId();
      GerritApi gApi = server.getTestInjector().getInstance(GerritApi.class);
      gApi.projects().create("project");

      ChangeInput in = new ChangeInput(project.get(), "master", "Test change");
      in.newBranch = true;
      changeId = new Change.Id(gApi.changes().create(in).info()._number);
    }
  }

  private GerritServer startServer() throws Exception {
    return GerritServer.start(serverDesc, new Config(), site);
  }

  private ManualRequestContext openContext(GerritServer server) throws Exception {
    Injector i = server.getTestInjector();
    TestAccount a = i.getInstance(AccountCreator.class).admin();
    return openContext(server, a.getId());
  }

  private ManualRequestContext openContext(GerritServer server, Account.Id accountId)
      throws Exception {
    return server.getTestInjector().getInstance(OneOffRequestContext.class).openAs(accountId);
  }

  private void migrate(String... additionalArgs) throws Exception {
    String[] args =
        Stream.concat(
                Stream.of("migrate-to-note-db", "-d", site.toString(), "--show-stack-trace"),
                Stream.of(additionalArgs))
            .toArray(String[]::new);
    assertThat(GerritLauncher.mainImpl(args)).isEqualTo(0);
  }

  private void assertNotesMigrationState(NotesMigrationState expected) throws Exception {
    gerritConfig.load();
    assertThat(NotesMigrationState.forNotesMigration(new ConfigNotesMigration(gerritConfig)))
        .hasValue(expected);
  }

  private ReviewDb openUnderlyingReviewDb(GerritServer server) throws Exception {
    return server
        .getTestInjector()
        .getInstance(Key.get(new TypeLiteral<SchemaFactory<ReviewDb>>() {}, ReviewDbFactory.class))
        .open();
  }
}
