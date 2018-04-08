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
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.common.truth.TruthJUnit.assume;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.StandaloneSiteTest;
import com.google.gerrit.config.AllUsersName;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.common.ChangeInput;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.LocalDiskRepositoryManager;
import com.google.gerrit.server.index.GerritIndexStatus;
import com.google.gerrit.server.index.change.ChangeIndexCollection;
import com.google.gerrit.server.index.change.ChangeSchemaDefinitions;
import com.google.gerrit.server.notedb.NoteDbChangeState;
import com.google.gerrit.server.notedb.NoteDbChangeState.PrimaryStorage;
import com.google.gerrit.server.notedb.NoteDbChangeState.RefState;
import com.google.gerrit.server.notedb.NotesMigrationState;
import com.google.gerrit.server.schema.ReviewDbFactory;
import com.google.gerrit.testing.NoteDbMode;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for NoteDb migrations where the entry point is through a program, {@code
 * migrate-to-note-db} or {@code daemon}.
 *
 * <p><strong>Note:</strong> These tests are very slow due to the repeated daemon startup. Prefer
 * adding tests to {@link com.google.gerrit.acceptance.server.notedb.OnlineNoteDbMigrationIT} if
 * possible.
 */
@NoHttpd
public class StandaloneNoteDbMigrationIT extends StandaloneSiteTest {
  private StoredConfig gerritConfig;
  private StoredConfig noteDbConfig;

  private Project.NameKey project;
  private Change.Id changeId;

  @Before
  public void setUp() throws Exception {
    assume().that(NoteDbMode.get()).isEqualTo(NoteDbMode.OFF);
    gerritConfig = new FileBasedConfig(sitePaths.gerrit_config.toFile(), FS.detect());
    // Unlike in the running server, for tests, we don't stack notedb.config on gerrit.config.
    noteDbConfig = new FileBasedConfig(sitePaths.notedb_config.toFile(), FS.detect());

    // Set gc.pruneExpire=now so GC prunes all unreachable objects from All-Users, which allows us
    // to reliably test that it behaves as expected.
    Path cfgPath = sitePaths.site_path.resolve("git").resolve("All-Users.git").resolve("config");
    assertWithMessage("Expected All-Users config at %s", cfgPath)
        .that(Files.isRegularFile(cfgPath))
        .isTrue();
    FileBasedConfig cfg = new FileBasedConfig(cfgPath.toFile(), FS.detect());
    cfg.setString("gc", null, "pruneExpire", "now");
    cfg.save();
  }

  @Test
  public void rebuildOneChangeTrialMode() throws Exception {
    assertNoAutoMigrateConfig(gerritConfig);
    assertNoAutoMigrateConfig(noteDbConfig);
    assertNotesMigrationState(NotesMigrationState.REVIEW_DB);
    setUpOneChange();

    migrate("--trial");
    assertNotesMigrationState(NotesMigrationState.READ_WRITE_NO_SEQUENCE);

    try (ServerContext ctx = startServer()) {
      GitRepositoryManager repoManager = ctx.getInjector().getInstance(GitRepositoryManager.class);
      ObjectId metaId;
      try (Repository repo = repoManager.openRepository(project)) {
        Ref ref = repo.exactRef(RefNames.changeMetaRef(changeId));
        assertThat(ref).isNotNull();
        metaId = ref.getObjectId();
      }

      try (ReviewDb db = openUnderlyingReviewDb(ctx)) {
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
    assertNoAutoMigrateConfig(gerritConfig);
    assertNoAutoMigrateConfig(noteDbConfig);
    assertNotesMigrationState(NotesMigrationState.REVIEW_DB);
    setUpOneChange();

    migrate();
    assertNotesMigrationState(NotesMigrationState.NOTE_DB);

    File allUsersDir;
    try (ServerContext ctx = startServer()) {
      GitRepositoryManager repoManager = ctx.getInjector().getInstance(GitRepositoryManager.class);
      try (Repository repo = repoManager.openRepository(project)) {
        assertThat(repo.exactRef(RefNames.changeMetaRef(changeId))).isNotNull();
      }
      assertThat(repoManager).isInstanceOf(LocalDiskRepositoryManager.class);
      try (Repository repo =
          repoManager.openRepository(ctx.getInjector().getInstance(AllUsersName.class))) {
        allUsersDir = repo.getDirectory();
      }

      try (ReviewDb db = openUnderlyingReviewDb(ctx)) {
        Change c = db.changes().get(changeId);
        assertThat(c).isNotNull();
        NoteDbChangeState state = NoteDbChangeState.parse(c);
        assertThat(state).isNotNull();
        assertThat(state.getPrimaryStorage()).isEqualTo(PrimaryStorage.NOTE_DB);
        assertThat(state.getRefState()).isEmpty();

        ChangeInput in = new ChangeInput(project.get(), "master", "NoteDb-only change");
        in.newBranch = true;
        GerritApi gApi = ctx.getInjector().getInstance(GerritApi.class);
        Change.Id id2 = new Change.Id(gApi.changes().create(in).info()._number);
        assertThat(db.changes().get(id2)).isNull();
      }
    }
    assertNoAutoMigrateConfig(gerritConfig);
    assertAutoMigrateConfig(noteDbConfig, false);

    try (FileRepository repo = new FileRepository(allUsersDir)) {
      try (Stream<Path> paths = Files.walk(repo.getObjectsDirectory().toPath())) {
        assertThat(paths.filter(p -> !p.toString().contains("pack") && Files.isRegularFile(p)))
            .named("loose object files in All-Users")
            .isEmpty();
      }
      assertThat(repo.getObjectDatabase().getPacks()).named("packfiles in All-Users").hasSize(1);
    }
  }

  @Test
  public void migrationWithReindex() throws Exception {
    assertNotesMigrationState(NotesMigrationState.REVIEW_DB);
    setUpOneChange();

    int version = ChangeSchemaDefinitions.INSTANCE.getLatest().getVersion();
    GerritIndexStatus status = new GerritIndexStatus(sitePaths);
    assertThat(status.getReady(ChangeSchemaDefinitions.NAME, version)).isTrue();
    status.setReady(ChangeSchemaDefinitions.NAME, version, false);
    status.save();
    assertServerStartupFails();

    migrate();
    assertNotesMigrationState(NotesMigrationState.NOTE_DB);

    status = new GerritIndexStatus(sitePaths);
    assertThat(status.getReady(ChangeSchemaDefinitions.NAME, version)).isTrue();
  }

  @Test
  public void onlineMigrationViaDaemon() throws Exception {
    assertNoAutoMigrateConfig(gerritConfig);
    assertNoAutoMigrateConfig(noteDbConfig);

    testOnlineMigration(u -> startServer(u.module(), "--migrate-to-note-db", "true"));

    assertNoAutoMigrateConfig(gerritConfig);
    assertAutoMigrateConfig(noteDbConfig, false);
  }

  @Test
  public void onlineMigrationViaConfig() throws Exception {
    assertNoAutoMigrateConfig(gerritConfig);
    assertNoAutoMigrateConfig(noteDbConfig);

    testOnlineMigration(
        u -> {
          gerritConfig.setBoolean("noteDb", "changes", "autoMigrate", true);
          gerritConfig.save();
          return startServer(u.module());
        });

    // Auto-migration is turned off in notedb.config, which takes precedence, but is still on in
    // gerrit.config. This means Puppet can continue overwriting gerrit.config without turning
    // auto-migration back on.
    assertAutoMigrateConfig(gerritConfig, true);
    assertAutoMigrateConfig(noteDbConfig, false);
  }

  @Test
  public void onlineMigrationTrialModeViaFlag() throws Exception {
    assertNoAutoMigrateConfig(gerritConfig);
    assertNoTrialConfig(gerritConfig);

    assertNoAutoMigrateConfig(noteDbConfig);
    assertNoTrialConfig(noteDbConfig);

    testOnlineMigration(
        u -> startServer(u.module(), "--migrate-to-note-db", "--trial"),
        NotesMigrationState.READ_WRITE_NO_SEQUENCE);

    assertNoAutoMigrateConfig(gerritConfig);
    assertNoTrialConfig(gerritConfig);

    assertAutoMigrateConfig(noteDbConfig, true);
    assertTrialConfig(noteDbConfig, true);
  }

  @Test
  public void onlineMigrationTrialModeViaConfig() throws Exception {
    assertNoAutoMigrateConfig(gerritConfig);
    assertNoTrialConfig(gerritConfig);

    assertNoAutoMigrateConfig(noteDbConfig);
    assertNoTrialConfig(noteDbConfig);

    testOnlineMigration(
        u -> {
          gerritConfig.setBoolean("noteDb", "changes", "autoMigrate", true);
          gerritConfig.setBoolean("noteDb", "changes", "trial", true);
          gerritConfig.save();
          return startServer(u.module());
        },
        NotesMigrationState.READ_WRITE_NO_SEQUENCE);

    assertAutoMigrateConfig(gerritConfig, true);
    assertTrialConfig(gerritConfig, true);

    assertAutoMigrateConfig(noteDbConfig, true);
    assertTrialConfig(noteDbConfig, true);
  }

  @FunctionalInterface
  private interface StartServerWithMigration {
    ServerContext start(IndexUpgradeController u) throws Exception;
  }

  private void testOnlineMigration(StartServerWithMigration start) throws Exception {
    testOnlineMigration(start, NotesMigrationState.NOTE_DB);
  }

  private void testOnlineMigration(
      StartServerWithMigration start, NotesMigrationState expectedEndState) throws Exception {
    assertNotesMigrationState(NotesMigrationState.REVIEW_DB);
    int prevVersion = ChangeSchemaDefinitions.INSTANCE.getPrevious().getVersion();
    int currVersion = ChangeSchemaDefinitions.INSTANCE.getLatest().getVersion();

    // Before storing any changes, switch back to the previous version.
    GerritIndexStatus status = new GerritIndexStatus(sitePaths);
    status.setReady(ChangeSchemaDefinitions.NAME, currVersion, false);
    status.setReady(ChangeSchemaDefinitions.NAME, prevVersion, true);
    status.save();

    setOnlineUpgradeConfig(false);
    setUpOneChange();
    setOnlineUpgradeConfig(true);

    IndexUpgradeController u = new IndexUpgradeController(1);
    try (ServerContext ctx = start.start(u)) {
      ChangeIndexCollection indexes = ctx.getInjector().getInstance(ChangeIndexCollection.class);
      assertThat(indexes.getSearchIndex().getSchema().getVersion()).isEqualTo(prevVersion);

      // Index schema upgrades happen after NoteDb migration, so waiting for those to complete
      // should be sufficient.
      u.runUpgrades();

      assertThat(indexes.getSearchIndex().getSchema().getVersion()).isEqualTo(currVersion);
      assertNotesMigrationState(expectedEndState);
    }
  }

  private void setUpOneChange() throws Exception {
    project = new Project.NameKey("project");
    try (ServerContext ctx = startServer()) {
      GerritApi gApi = ctx.getInjector().getInstance(GerritApi.class);
      gApi.projects().create("project");

      ChangeInput in = new ChangeInput(project.get(), "master", "Test change");
      in.newBranch = true;
      changeId = new Change.Id(gApi.changes().create(in).info()._number);
    }
  }

  private void migrate(String... additionalArgs) throws Exception {
    runGerrit(
        ImmutableList.of(
            "migrate-to-note-db", "-d", sitePaths.site_path.toString(), "--show-stack-trace"),
        ImmutableList.copyOf(additionalArgs));
  }

  private void assertNotesMigrationState(NotesMigrationState expected) throws Exception {
    noteDbConfig.load();
    assertThat(NotesMigrationState.forConfig(noteDbConfig)).hasValue(expected);
  }

  private ReviewDb openUnderlyingReviewDb(ServerContext ctx) throws Exception {
    return ctx.getInjector()
        .getInstance(Key.get(new TypeLiteral<SchemaFactory<ReviewDb>>() {}, ReviewDbFactory.class))
        .open();
  }

  private static void assertNoAutoMigrateConfig(StoredConfig cfg) throws Exception {
    cfg.load();
    assertThat(cfg.getString("noteDb", "changes", "autoMigrate")).isNull();
  }

  private static void assertAutoMigrateConfig(StoredConfig cfg, boolean expected) throws Exception {
    cfg.load();
    assertThat(cfg.getString("noteDb", "changes", "autoMigrate")).isNotNull();
    assertThat(cfg.getBoolean("noteDb", "changes", "autoMigrate", false)).isEqualTo(expected);
  }

  private static void assertNoTrialConfig(StoredConfig cfg) throws Exception {
    cfg.load();
    assertThat(cfg.getString("noteDb", "changes", "trial")).isNull();
  }

  private static void assertTrialConfig(StoredConfig cfg, boolean expected) throws Exception {
    cfg.load();
    assertThat(cfg.getString("noteDb", "changes", "trial")).isNotNull();
    assertThat(cfg.getBoolean("noteDb", "changes", "trial", false)).isEqualTo(expected);
  }

  private void setOnlineUpgradeConfig(boolean enable) throws Exception {
    gerritConfig.load();
    gerritConfig.setBoolean("index", null, "onlineUpgrade", enable);
    gerritConfig.save();
  }
}
