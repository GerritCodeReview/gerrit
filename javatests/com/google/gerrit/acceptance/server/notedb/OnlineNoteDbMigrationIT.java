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

import static com.google.common.collect.ImmutableSortedSet.toImmutableSortedSet;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.common.truth.TruthJUnit.assume;
import static com.google.gerrit.server.notedb.NoteDbChangeState.NOTE_DB_PRIMARY_STATE;
import static com.google.gerrit.server.notedb.NotesMigrationState.NOTE_DB;
import static com.google.gerrit.server.notedb.NotesMigrationState.READ_WRITE_NO_SEQUENCE;
import static com.google.gerrit.server.notedb.NotesMigrationState.READ_WRITE_WITH_SEQUENCE_NOTE_DB_PRIMARY;
import static com.google.gerrit.server.notedb.NotesMigrationState.READ_WRITE_WITH_SEQUENCE_REVIEW_DB_PRIMARY;
import static com.google.gerrit.server.notedb.NotesMigrationState.REVIEW_DB;
import static com.google.gerrit.server.notedb.NotesMigrationState.WRITE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Comparator.naturalOrder;
import static org.easymock.EasyMock.createStrictMock;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GerritConfig;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.Sandboxed;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.config.SitePaths;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CommentsUtil;
import com.google.gerrit.server.Sequences;
import com.google.gerrit.server.notedb.ChangeBundle;
import com.google.gerrit.server.notedb.ChangeBundleReader;
import com.google.gerrit.server.notedb.NoteDbChangeState;
import com.google.gerrit.server.notedb.NoteDbChangeState.PrimaryStorage;
import com.google.gerrit.server.notedb.NoteDbChangeState.RefState;
import com.google.gerrit.server.notedb.NotesMigrationState;
import com.google.gerrit.server.notedb.rebuild.MigrationException;
import com.google.gerrit.server.notedb.rebuild.NoteDbMigrator;
import com.google.gerrit.server.notedb.rebuild.NotesMigrationStateListener;
import com.google.gerrit.server.schema.ReviewDbFactory;
import com.google.gerrit.testing.ConfigSuite;
import com.google.gerrit.testing.NoteDbMode;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

@Sandboxed
@UseLocalDisk
@NoHttpd
public class OnlineNoteDbMigrationIT extends AbstractDaemonTest {
  private static final String INVALID_STATE = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef";

  @ConfigSuite.Default
  public static Config defaultConfig() {
    Config cfg = new Config();
    cfg.setInt("noteDb", "changes", "sequenceBatchSize", 10);
    cfg.setInt("noteDb", "changes", "initialSequenceGap", 500);
    return cfg;
  }

  // Tests in this class are generally interested in the actual ReviewDb contents, but the shifting
  // migration state may result in various kinds of wrappers showing up unexpectedly.
  @Inject @ReviewDbFactory private SchemaFactory<ReviewDb> schemaFactory;

  @Inject private ChangeBundleReader changeBundleReader;
  @Inject private CommentsUtil commentsUtil;
  @Inject private DynamicSet<NotesMigrationStateListener> listeners;
  @Inject private Provider<NoteDbMigrator.Builder> migratorBuilderProvider;
  @Inject private Sequences sequences;
  @Inject private SitePaths sitePaths;

  private FileBasedConfig noteDbConfig;
  private List<RegistrationHandle> addedListeners;

  @Before
  public void setUp() throws Exception {
    assume().that(NoteDbMode.get()).isEqualTo(NoteDbMode.OFF);
    // Unlike in the running server, for tests, we don't stack notedb.config on gerrit.config.
    noteDbConfig = new FileBasedConfig(sitePaths.notedb_config.toFile(), FS.detect());
    assertNotesMigrationState(REVIEW_DB, false, false);
    addedListeners = new ArrayList<>();
  }

  @After
  public void tearDown() throws Exception {
    if (addedListeners != null) {
      addedListeners.forEach(RegistrationHandle::remove);
      addedListeners = null;
    }
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
        "Cannot set changes or projects during full migration",
        b -> b.setChanges(cs),
        NoteDbMigrator::migrate);
    assertMigrationException(
        "Cannot set changes or projects during full migration",
        b -> b.setProjects(ps),
        NoteDbMigrator::migrate);

    setNotesMigrationState(READ_WRITE_WITH_SEQUENCE_REVIEW_DB_PRIMARY);
    assertMigrationException(
        "Migration has already progressed past the endpoint of the \"trial mode\" state",
        b -> b.setTrialMode(true),
        NoteDbMigrator::migrate);

    setNotesMigrationState(READ_WRITE_WITH_SEQUENCE_NOTE_DB_PRIMARY);
    assertMigrationException(
        "Cannot force rebuild changes; NoteDb is already the primary storage for some changes",
        b -> b.setForceRebuild(true),
        NoteDbMigrator::migrate);
  }

  @Test
  @GerritConfig(name = "noteDb.changes.initialSequenceGap", value = "-7")
  public void initialSequenceGapMustBeNonNegative() throws Exception {
    setNotesMigrationState(READ_WRITE_NO_SEQUENCE);
    assertMigrationException("Sequence gap must be non-negative: -7", b -> b, m -> {});
  }

  @Test
  public void rebuildOneChangeTrialModeAndForceRebuild() throws Exception {
    PushOneCommit.Result r = createChange();
    Change.Id id = r.getChange().getId();

    migrate(b -> b.setTrialMode(true));
    assertNotesMigrationState(READ_WRITE_NO_SEQUENCE, false, true);

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
    assertNotesMigrationState(READ_WRITE_NO_SEQUENCE, false, true);

    try (Repository repo = repoManager.openRepository(project);
        ReviewDb db = schemaFactory.open()) {
      // Change is out of date, but was not rebuilt without forceRebuild.
      assertThat(repo.exactRef(RefNames.changeMetaRef(id)).getObjectId()).isEqualTo(oldMetaId);
      Change c = db.changes().get(id);
      assertThat(c.getNoteDbState()).isEqualTo(INVALID_STATE);
    }

    migrate(b -> b.setTrialMode(true).setForceRebuild(true));
    assertNotesMigrationState(READ_WRITE_NO_SEQUENCE, false, true);

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
  public void autoMigrateTrialMode() throws Exception {
    PushOneCommit.Result r = createChange();
    Change.Id id = r.getChange().getId();

    migrate(b -> b.setAutoMigrate(true).setTrialMode(true).setStopAtStateForTesting(WRITE));
    assertNotesMigrationState(WRITE, true, true);

    migrate(b -> b);
    // autoMigrate is still enabled so that we can continue the migration by only unsetting trial.
    assertNotesMigrationState(READ_WRITE_NO_SEQUENCE, true, true);

    ObjectId metaId;
    try (Repository repo = repoManager.openRepository(project);
        ReviewDb db = schemaFactory.open()) {
      Ref ref = repo.exactRef(RefNames.changeMetaRef(id));
      assertThat(ref).isNotNull();
      metaId = ref.getObjectId();
      NoteDbChangeState state = NoteDbChangeState.parse(db.changes().get(id));
      assertThat(state).isNotNull();
      assertThat(state.getPrimaryStorage()).isEqualTo(PrimaryStorage.REVIEW_DB);
      assertThat(state.getRefState()).hasValue(RefState.create(metaId, ImmutableMap.of()));
    }

    // Unset trial mode and the next migration runs to completion.
    noteDbConfig.load();
    NoteDbMigrator.setTrialMode(noteDbConfig, false);
    noteDbConfig.save();

    migrate(b -> b);
    assertNotesMigrationState(NOTE_DB, false, false);

    try (Repository repo = repoManager.openRepository(project);
        ReviewDb db = schemaFactory.open()) {
      Ref ref = repo.exactRef(RefNames.changeMetaRef(id));
      assertThat(ref).isNotNull();
      assertThat(ref.getObjectId()).isEqualTo(metaId);
      NoteDbChangeState state = NoteDbChangeState.parse(db.changes().get(id));
      assertThat(state).isNotNull();
      assertThat(state.getPrimaryStorage()).isEqualTo(PrimaryStorage.NOTE_DB);
    }
  }

  @Test
  public void rebuildSubsetOfChanges() throws Exception {
    setNotesMigrationState(WRITE);

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
    setNotesMigrationState(WRITE);

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

  @Test
  public void enableSequencesNoGap() throws Exception {
    testEnableSequences(0, 2, "12");
  }

  @Test
  public void enableSequencesWithGap() throws Exception {
    testEnableSequences(-1, 502, "512");
  }

  private void testEnableSequences(int builderOption, int expectedFirstId, String expectedRefValue)
      throws Exception {
    PushOneCommit.Result r = createChange();
    Change.Id id = r.getChange().getId();
    assertThat(id.get()).isEqualTo(1);

    migrate(
        b ->
            b.setSequenceGap(builderOption)
                .setStopAtStateForTesting(READ_WRITE_WITH_SEQUENCE_REVIEW_DB_PRIMARY));

    assertThat(sequences.nextChangeId()).isEqualTo(expectedFirstId);
    assertThat(sequences.nextChangeId()).isEqualTo(expectedFirstId + 1);

    try (Repository repo = repoManager.openRepository(allProjects);
        ObjectReader reader = repo.newObjectReader()) {
      Ref ref = repo.exactRef("refs/sequences/changes");
      assertThat(ref).isNotNull();
      ObjectLoader loader = reader.open(ref.getObjectId());
      assertThat(loader.getType()).isEqualTo(Constants.OBJ_BLOB);
      // Acquired a block of 10 to serve the first nextChangeId call after migration.
      assertThat(new String(loader.getCachedBytes(), UTF_8)).isEqualTo(expectedRefValue);
    }

    try (ReviewDb db = schemaFactory.open()) {
      // Underlying, unused ReviewDb is still on its own sequence.
      @SuppressWarnings("deprecation")
      int nextFromReviewDb = db.nextChangeId();
      assertThat(nextFromReviewDb).isEqualTo(3);
    }
  }

  @Test
  public void fullMigrationSameThread() throws Exception {
    testFullMigration(1);
  }

  @Test
  public void fullMigrationMultipleThreads() throws Exception {
    testFullMigration(2);
  }

  private void testFullMigration(int threads) throws Exception {
    PushOneCommit.Result r1 = createChange();
    PushOneCommit.Result r2 = createChange();
    Change.Id id1 = r1.getChange().getId();
    Change.Id id2 = r2.getChange().getId();

    Set<String> objectFiles = getObjectFiles(project);
    assertThat(objectFiles).isNotEmpty();

    migrate(b -> b.setThreads(threads));

    assertNotesMigrationState(NOTE_DB, false, false);
    assertThat(sequences.nextChangeId()).isEqualTo(503);
    assertThat(getObjectFiles(project)).containsExactlyElementsIn(objectFiles);

    ObjectId oldMetaId = null;
    int rowVersion = 0;
    try (ReviewDb db = schemaFactory.open();
        Repository repo = repoManager.openRepository(project)) {
      for (Change.Id id : ImmutableList.of(id1, id2)) {
        String refName = RefNames.changeMetaRef(id);
        Ref ref = repo.exactRef(refName);
        assertThat(ref).named(refName).isNotNull();

        Change c = db.changes().get(id);
        assertThat(c.getTopic()).named("topic of change %s", id).isNull();
        NoteDbChangeState s = NoteDbChangeState.parse(c);
        assertThat(s.getPrimaryStorage())
            .named("primary storage of change %s", id)
            .isEqualTo(PrimaryStorage.NOTE_DB);
        assertThat(s.getRefState()).named("ref state of change %s").isEmpty();

        if (id.equals(id1)) {
          oldMetaId = ref.getObjectId();
          rowVersion = c.getRowVersion();
        }
      }
    }

    // Do not open a new context, to simulate races with other threads that opened a context earlier
    // in the migration process; this needs to work.
    gApi.changes().id(id1.get()).topic(name("a-topic"));

    // Of course, it should also work with a new context.
    resetCurrentApiUser();
    gApi.changes().id(id1.get()).topic(name("another-topic"));

    try (ReviewDb db = schemaFactory.open();
        Repository repo = repoManager.openRepository(project)) {
      assertThat(repo.exactRef(RefNames.changeMetaRef(id1)).getObjectId()).isNotEqualTo(oldMetaId);

      Change c = db.changes().get(id1);
      assertThat(c.getTopic()).isNull();
      assertThat(c.getRowVersion()).isEqualTo(rowVersion);
    }
  }

  @Test
  public void fullMigrationOneChangeWithNoPatchSets() throws Exception {
    PushOneCommit.Result r1 = createChange();
    PushOneCommit.Result r2 = createChange();
    Change.Id id1 = r1.getChange().getId();
    Change.Id id2 = r2.getChange().getId();

    db.changes().beginTransaction(id2);
    try {
      db.patchSets().delete(db.patchSets().byChange(id2));
      db.commit();
    } finally {
      db.rollback();
    }

    migrate(b -> b);
    assertNotesMigrationState(NOTE_DB, false, false);

    try (ReviewDb db = schemaFactory.open();
        Repository repo = repoManager.openRepository(project)) {
      assertThat(repo.exactRef(RefNames.changeMetaRef(id1))).isNotNull();
      assertThat(db.changes().get(id1).getNoteDbState()).isEqualTo(NOTE_DB_PRIMARY_STATE);

      // A change with no patch sets is so corrupt that it is completely skipped by the migration
      // process.
      assertThat(repo.exactRef(RefNames.changeMetaRef(id2))).isNull();
      assertThat(db.changes().get(id2).getNoteDbState()).isNull();
    }
  }

  @Test
  public void fullMigrationMissingPatchSetRefs() throws Exception {
    PushOneCommit.Result r = createChange();
    Change.Id id = r.getChange().getId();

    try (Repository repo = repoManager.openRepository(project)) {
      RefUpdate u = repo.updateRef(new PatchSet.Id(id, 1).toRefName());
      u.setForceUpdate(true);
      assertThat(u.delete()).isEqualTo(RefUpdate.Result.FORCED);
    }

    ChangeBundle reviewDbBundle;
    try (ReviewDb db = schemaFactory.open()) {
      reviewDbBundle = changeBundleReader.fromReviewDb(db, id);
    }

    migrate(b -> b);
    assertNotesMigrationState(NOTE_DB, false, false);

    try (ReviewDb db = schemaFactory.open();
        Repository repo = repoManager.openRepository(project)) {
      // Change migrated successfully even though it was missing patch set refs.
      assertThat(repo.exactRef(RefNames.changeMetaRef(id))).isNotNull();
      assertThat(db.changes().get(id).getNoteDbState()).isEqualTo(NOTE_DB_PRIMARY_STATE);

      ChangeBundle noteDbBundle =
          ChangeBundle.fromNotes(commentsUtil, notesFactory.createChecked(db, project, id));
      assertThat(noteDbBundle.differencesFrom(reviewDbBundle)).isEmpty();
    }
  }

  @Test
  public void autoMigrationConfig() throws Exception {
    createChange();

    migrate(b -> b.setStopAtStateForTesting(WRITE));
    assertNotesMigrationState(WRITE, false, false);

    migrate(b -> b.setAutoMigrate(true).setStopAtStateForTesting(READ_WRITE_NO_SEQUENCE));
    assertNotesMigrationState(READ_WRITE_NO_SEQUENCE, true, false);

    migrate(b -> b);
    assertNotesMigrationState(NOTE_DB, false, false);
  }

  @Test
  public void notesMigrationStateListener() throws Exception {
    NotesMigrationStateListener listener = createStrictMock(NotesMigrationStateListener.class);
    listener.preStateChange(REVIEW_DB, WRITE);
    expectLastCall();
    listener.preStateChange(WRITE, READ_WRITE_NO_SEQUENCE);
    expectLastCall();
    listener.preStateChange(READ_WRITE_NO_SEQUENCE, READ_WRITE_WITH_SEQUENCE_REVIEW_DB_PRIMARY);
    expectLastCall();
    listener.preStateChange(
        READ_WRITE_WITH_SEQUENCE_REVIEW_DB_PRIMARY, READ_WRITE_WITH_SEQUENCE_NOTE_DB_PRIMARY);
    listener.preStateChange(READ_WRITE_WITH_SEQUENCE_NOTE_DB_PRIMARY, NOTE_DB);
    expectLastCall();
    replay(listener);
    addListener(listener);

    createChange();
    migrate(b -> b);
    assertNotesMigrationState(NOTE_DB, false, false);
    verify(listener);
  }

  @Test
  public void notesMigrationStateListenerFails() throws Exception {
    NotesMigrationStateListener listener = createStrictMock(NotesMigrationStateListener.class);
    listener.preStateChange(REVIEW_DB, WRITE);
    expectLastCall();
    listener.preStateChange(WRITE, READ_WRITE_NO_SEQUENCE);
    IOException listenerException = new IOException("Listener failed");
    expectLastCall().andThrow(listenerException);
    replay(listener);
    addListener(listener);

    createChange();
    try {
      migrate(b -> b);
      fail("expected IOException");
    } catch (IOException e) {
      assertThat(e).isSameAs(listenerException);
    }
    assertNotesMigrationState(WRITE, false, false);
    verify(listener);
  }

  private void assertNotesMigrationState(
      NotesMigrationState expected, boolean autoMigrate, boolean trialMode) throws Exception {
    assertThat(NotesMigrationState.forNotesMigration(notesMigration)).hasValue(expected);
    noteDbConfig.load();
    assertThat(NotesMigrationState.forConfig(noteDbConfig)).hasValue(expected);
    assertThat(NoteDbMigrator.getAutoMigrate(noteDbConfig))
        .named("noteDb.changes.autoMigrate")
        .isEqualTo(autoMigrate);
    assertThat(NoteDbMigrator.getTrialMode(noteDbConfig))
        .named("noteDb.changes.trial")
        .isEqualTo(trialMode);
  }

  private void setNotesMigrationState(NotesMigrationState state) throws Exception {
    noteDbConfig.load();
    state.setConfigValues(noteDbConfig);
    noteDbConfig.save();
    notesMigration.setFrom(state);
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
      fail("expected MigrationException");
    } catch (MigrationException e) {
      assertThat(e).hasMessageThat().contains(expectMessageContains);
    }
  }

  private void addListener(NotesMigrationStateListener listener) {
    addedListeners.add(listeners.add(listener));
  }

  private ImmutableSortedSet<String> getObjectFiles(Project.NameKey project) throws Exception {
    try (Repository repo = repoManager.openRepository(project);
        Stream<Path> paths =
            Files.walk(((FileRepository) repo).getObjectDatabase().getDirectory().toPath())) {
      return paths
          .filter(path -> !Files.isDirectory(path))
          .map(Path::toString)
          .filter(name -> !name.endsWith(".pack") && !name.endsWith(".idx"))
          .collect(toImmutableSortedSet(naturalOrder()));
    }
  }
}
