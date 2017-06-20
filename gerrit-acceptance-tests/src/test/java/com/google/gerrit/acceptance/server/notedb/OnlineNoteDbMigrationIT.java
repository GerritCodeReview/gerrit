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

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.Sandboxed;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDbUtil;
import com.google.gerrit.server.notedb.NoteDbChangeState;
import com.google.gerrit.server.notedb.NoteDbChangeState.PrimaryStorage;
import com.google.gerrit.server.notedb.NoteDbChangeState.RefState;
import com.google.gerrit.server.notedb.NotesMigrationState;
import com.google.gerrit.server.notedb.rebuild.NoteDbMigrator;
import com.google.gerrit.testutil.NoteDbMode;
import com.google.inject.Inject;
import com.google.inject.Provider;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.junit.Before;
import org.junit.Test;

@Sandboxed
@NoHttpd
public class OnlineNoteDbMigrationIT extends AbstractDaemonTest {
  @Inject private Provider<NoteDbMigrator.Builder> migratorBuilderProvider;

  @Before
  public void setUp() {
    assume().that(NoteDbMode.get()).isEqualTo(NoteDbMode.OFF);
    assertNotesMigrationState(NotesMigrationState.REVIEW_DB);
  }

  @Test
  public void rebuildOneChangeTrialMode() throws Exception {
    PushOneCommit.Result r = createChange();
    Change.Id id = r.getChange().getId();

    try (NoteDbMigrator migrator = migratorBuilderProvider.get().setTrialMode(true).build()) {
      migrator.migrate();
    }
    assertNotesMigrationState(NotesMigrationState.READ_WRITE_NO_SEQUENCE);

    ObjectId metaId;
    try (Repository repo = repoManager.openRepository(project)) {
      Ref ref = repo.exactRef(RefNames.changeMetaRef(id));
      assertThat(ref).isNotNull();
      metaId = ref.getObjectId();
    }

    Change c = ReviewDbUtil.unwrapDb(db).changes().get(id);
    assertThat(c).isNotNull();
    NoteDbChangeState state = NoteDbChangeState.parse(c);
    assertThat(state).isNotNull();
    assertThat(state.getPrimaryStorage()).isEqualTo(PrimaryStorage.REVIEW_DB);
    assertThat(state.getRefState()).hasValue(RefState.create(metaId, ImmutableMap.of()));
  }

  private void assertNotesMigrationState(NotesMigrationState expected) {
    assertThat(NotesMigrationState.forNotesMigration(notesMigration)).hasValue(expected);
  }
}
