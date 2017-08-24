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
import static com.google.common.truth.TruthJUnit.assume;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.StandaloneSiteTest;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.common.ChangeInput;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.index.GerritIndexStatus;
import com.google.gerrit.server.index.change.ChangeIndexCollection;
import com.google.gerrit.server.index.change.ChangeSchemaDefinitions;
import com.google.gerrit.server.notedb.NoteDbChangeState;
import com.google.gerrit.server.notedb.NoteDbChangeState.PrimaryStorage;
import com.google.gerrit.server.notedb.NoteDbChangeState.RefState;
import com.google.gerrit.server.notedb.NotesMigrationState;
import com.google.gerrit.server.schema.ReviewDbFactory;
import com.google.gerrit.testutil.NoteDbMode;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
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

  private Project.NameKey project;
  private Change.Id changeId;

  @Before
  public void setUp() throws Exception {
    assume().that(NoteDbMode.get()).isEqualTo(NoteDbMode.OFF);
    gerritConfig = new FileBasedConfig(sitePaths.gerrit_config.toFile(), FS.detect());
  }

  @Test
  public void rebuildOneChangeTrialMode() throws Exception {
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
    assertNotesMigrationState(NotesMigrationState.REVIEW_DB);
    setUpOneChange();

    migrate();
    assertNotesMigrationState(NotesMigrationState.NOTE_DB);

    try (ServerContext ctx = startServer()) {
      GitRepositoryManager repoManager = ctx.getInjector().getInstance(GitRepositoryManager.class);
      try (Repository repo = repoManager.openRepository(project)) {
        assertThat(repo.exactRef(RefNames.changeMetaRef(changeId))).isNotNull();
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
    try (ServerContext ctx = startServer(u.module(), "--migrate-to-note-db", "true")) {
      ChangeIndexCollection indexes = ctx.getInjector().getInstance(ChangeIndexCollection.class);
      assertThat(indexes.getSearchIndex().getSchema().getVersion()).isEqualTo(prevVersion);

      // Index schema upgrades happen after NoteDb migration, so waiting for those to complete
      // should be sufficient.
      u.runUpgrades();

      assertThat(indexes.getSearchIndex().getSchema().getVersion()).isEqualTo(currVersion);
      assertNotesMigrationState(NotesMigrationState.NOTE_DB);
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
    gerritConfig.load();
    assertThat(NotesMigrationState.forConfig(gerritConfig)).hasValue(expected);
  }

  private ReviewDb openUnderlyingReviewDb(ServerContext ctx) throws Exception {
    return ctx.getInjector()
        .getInstance(Key.get(new TypeLiteral<SchemaFactory<ReviewDb>>() {}, ReviewDbFactory.class))
        .open();
  }

  private void setOnlineUpgradeConfig(boolean enable) throws Exception {
    gerritConfig.load();
    gerritConfig.setBoolean("index", null, "onlineUpgrade", enable);
    gerritConfig.save();
  }
}
