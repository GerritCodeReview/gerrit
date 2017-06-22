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

package com.google.gerrit.acceptance.server.notedb;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.common.truth.TruthJUnit.assume;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.Sandboxed;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.notedb.ConfigNotesMigration;
import com.google.gerrit.server.notedb.NoteDbChangeState;
import com.google.gerrit.server.notedb.NoteDbChangeState.PrimaryStorage;
import com.google.gerrit.server.notedb.NoteDbChangeState.RefState;
import com.google.gerrit.server.notedb.NotesMigrationState;
import com.google.gerrit.server.notedb.rebuild.MigrationException;
import com.google.gerrit.server.notedb.rebuild.NoteDbMigrator;
import com.google.gerrit.server.schema.ReviewDbFactory;
import com.google.gerrit.testutil.NoteDbMode;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.List;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.junit.Before;
import org.junit.Test;

@Sandboxed
@NoHttpd
public class OnlineNoteDbMigrationIT extends AbstractDaemonTest {
  private static final String INVALID_STATE = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef";

  // Tests in this class are generally interested in the actual ReviewDb contents, but the shifting
  // migration state may result in various kinds of wrappers showing up unexpectedly.
  @Inject @ReviewDbFactory private SchemaFactory<ReviewDb> schemaFactory;

  @Inject private SitePaths sitePaths;
  @Inject private Provider<NoteDbMigrator.Builder> migratorBuilderProvider;

  private FileBasedConfig gerritConfig;

  @Before
  public void setUp() throws Exception {
    assume().that(NoteDbMode.get()).isEqualTo(NoteDbMode.OFF);
    gerritConfig = new FileBasedConfig(sitePaths.gerrit_config.toFile(), FS.detect());
    assertNotesMigrationState(NotesMigrationState.REVIEW_DB);
  }

  @Test
  public void preconditionsFail() throws Exception {
    List<Change.Id> cs = ImmutableList.of(new Change.Id(1));
    List<Project.NameKey> ps = ImmutableList.of(new Project.NameKey("p"));
    assertMigrationException(
        "Cannot rebuild without noteDb.changes.write=true", b -> b, NoteDbMigrator::rebuild);
    assertMigrationException(
        "Cannot set both changes and projects", b -> b.setChanges(cs).setProjects(ps), m -> {});
    assertMigrationException(
        "Cannot set changes or projects during auto-migration",
        b -> b.setChanges(cs),
        NoteDbMigrator::migrate);
    assertMigrationException(
        "Cannot set changes or projects during auto-migration",
        b -> b.setProjects(ps),
        NoteDbMigrator::migrate);

    setNotesMigrationState(NotesMigrationState.READ_WRITE_WITH_SEQUENCE_REVIEW_DB_PRIMARY);
    assertMigrationException(
        "Migration has already progressed past the endpoint of the \"trial mode\" state",
        b -> b.setTrialMode(true),
        NoteDbMigrator::migrate);

    setNotesMigrationState(NotesMigrationState.READ_WRITE_WITH_SEQUENCE_NOTE_DB_PRIMARY);
    assertMigrationException(
        "Cannot force rebuild changes; NoteDb is already the primary storage for some changes",
        b -> b.setForceRebuild(true),
        NoteDbMigrator::migrate);
  }

  @Test
  public void rebuildOneChangeTrialModeAndForceRebuild() throws Exception {
    PushOneCommit.Result r = createChange();
    Change.Id id = r.getChange().getId();

    try (NoteDbMigrator migrator = migratorBuilderProvider.get().setTrialMode(true).build()) {
      migrator.migrate();
    }
    assertNotesMigrationState(NotesMigrationState.READ_WRITE_NO_SEQUENCE);

    ObjectId oldMetaId;
    try (Repository repo = repoManager.openRepository(project);
        ReviewDb db = schemaFactory.open()) {
      Ref ref = repo.exactRef(RefNames.changeMetaRef(id));
      assertThat(ref).isNotNull();
      oldMetaId = ref.getObjectId();

      Change c = db.changes().get(id);
      assertThat(c).isNotNull();
      NoteDbChangeState state = NoteDbChangeState.parse(c);
      assertThat(state).isNotNull();
      assertThat(state.getPrimaryStorage()).isEqualTo(PrimaryStorage.REVIEW_DB);
      assertThat(state.getRefState()).hasValue(RefState.create(oldMetaId, ImmutableMap.of()));

      // Force change to be out of date, and change topic so it will get rebuilt as something other
      // than oldMetaId.
      c.setNoteDbState(INVALID_STATE);
      c.setTopic(name("a-new-topic"));
      db.changes().update(ImmutableList.of(c));
    }

    migrate(b -> b.setTrialMode(true));
    assertNotesMigrationState(NotesMigrationState.READ_WRITE_NO_SEQUENCE);

    try (Repository repo = repoManager.openRepository(project);
        ReviewDb db = schemaFactory.open()) {
      // Change is out of date, but was not rebuilt without forceRebuild.
      assertThat(repo.exactRef(RefNames.changeMetaRef(id)).getObjectId()).isEqualTo(oldMetaId);
      Change c = db.changes().get(id);
      assertThat(c.getNoteDbState()).isEqualTo(INVALID_STATE);
    }

    migrate(b -> b.setTrialMode(true).setForceRebuild(true));
    assertNotesMigrationState(NotesMigrationState.READ_WRITE_NO_SEQUENCE);

    try (Repository repo = repoManager.openRepository(project);
        ReviewDb db = schemaFactory.open()) {
      Ref ref = repo.exactRef(RefNames.changeMetaRef(id));
      assertThat(ref).isNotNull();
      ObjectId newMetaId = ref.getObjectId();
      assertThat(newMetaId).isNotEqualTo(oldMetaId);

      NoteDbChangeState state = NoteDbChangeState.parse(db.changes().get(id));
      assertThat(state).isNotNull();
      assertThat(state.getPrimaryStorage()).isEqualTo(PrimaryStorage.REVIEW_DB);
      assertThat(state.getRefState()).hasValue(RefState.create(newMetaId, ImmutableMap.of()));
    }
  }

  @Test
  public void rebuildSubsetOfChanges() throws Exception {
    setNotesMigrationState(NotesMigrationState.WRITE);

    PushOneCommit.Result r1 = createChange();
    PushOneCommit.Result r2 = createChange();
    Change.Id id1 = r1.getChange().getId();
    Change.Id id2 = r2.getChange().getId();

    try (ReviewDb db = schemaFactory.open()) {
      Change c1 = db.changes().get(id1);
      c1.setNoteDbState(INVALID_STATE);
      Change c2 = db.changes().get(id2);
      c2.setNoteDbState(INVALID_STATE);
      db.changes().update(ImmutableList.of(c1, c2));
    }

    migrate(b -> b.setChanges(ImmutableList.of(id2)), NoteDbMigrator::rebuild);

    try (ReviewDb db = schemaFactory.open()) {
      NoteDbChangeState s1 = NoteDbChangeState.parse(db.changes().get(id1));
      assertThat(s1.getChangeMetaId().name()).isEqualTo(INVALID_STATE);

      NoteDbChangeState s2 = NoteDbChangeState.parse(db.changes().get(id2));
      assertThat(s2.getChangeMetaId().name()).isNotEqualTo(INVALID_STATE);
    }
  }

  @Test
  public void rebuildSubsetOfProjects() throws Exception {
    setNotesMigrationState(NotesMigrationState.WRITE);

    Project.NameKey p2 = createProject("project2");
    TestRepository<?> tr2 = cloneProject(p2, admin);

    PushOneCommit.Result r1 = createChange();
    PushOneCommit.Result r2 = pushFactory.create(db, admin.getIdent(), tr2).to("refs/for/master");
    Change.Id id1 = r1.getChange().getId();
    Change.Id id2 = r2.getChange().getId();

    String invalidState = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef";
    try (ReviewDb db = schemaFactory.open()) {
      Change c1 = db.changes().get(id1);
      c1.setNoteDbState(invalidState);
      Change c2 = db.changes().get(id2);
      c2.setNoteDbState(invalidState);
      db.changes().update(ImmutableList.of(c1, c2));
    }

    migrate(b -> b.setProjects(ImmutableList.of(p2)), NoteDbMigrator::rebuild);

    try (ReviewDb db = schemaFactory.open()) {
      NoteDbChangeState s1 = NoteDbChangeState.parse(db.changes().get(id1));
      assertThat(s1.getChangeMetaId().name()).isEqualTo(invalidState);

      NoteDbChangeState s2 = NoteDbChangeState.parse(db.changes().get(id2));
      assertThat(s2.getChangeMetaId().name()).isNotEqualTo(invalidState);
    }
  }

  private void assertNotesMigrationState(NotesMigrationState expected) throws Exception {
    assertThat(NotesMigrationState.forNotesMigration(notesMigration)).hasValue(expected);
    gerritConfig.load();
    assertThat(NotesMigrationState.forNotesMigration(new ConfigNotesMigration(gerritConfig)))
        .hasValue(expected);
  }

  private void setNotesMigrationState(NotesMigrationState state) throws Exception {
    gerritConfig.load();
    ConfigNotesMigration.setConfigValues(gerritConfig, state.migration());
    gerritConfig.save();
    notesMigration.setFrom(state.migration());
  }

  @FunctionalInterface
  interface PrepareBuilder {
    NoteDbMigrator.Builder prepare(NoteDbMigrator.Builder b) throws Exception;
  }

  @FunctionalInterface
  interface RunMigration {
    void run(NoteDbMigrator m) throws Exception;
  }

  private void migrate(PrepareBuilder b) throws Exception {
    migrate(b, NoteDbMigrator::migrate);
  }

  private void migrate(PrepareBuilder b, RunMigration m) throws Exception {
    try (NoteDbMigrator migrator = b.prepare(migratorBuilderProvider.get()).build()) {
      m.run(migrator);
    }
  }

  private void assertMigrationException(
      String expectMessageContains, PrepareBuilder b, RunMigration m) throws Exception {
    try {
      migrate(b, m);
    } catch (MigrationException e) {
      assertThat(e).hasMessageThat().contains(expectMessageContains);
    }
  }
}
