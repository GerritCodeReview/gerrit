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

package com.google.gerrit.server.notedb;

import com.google.gerrit.server.notedb.NoteDbChangeState.PrimaryStorage;
import com.google.gerrit.server.notedb.NotesMigration.Snapshot;
import java.util.Optional;
import java.util.stream.Stream;
import org.eclipse.jgit.lib.Config;

/**
 * Possible high-level states of the NoteDb migration for changes.
 *
 * <p>This class describes the series of states required to migrate a site from ReviewDb-only to
 * NoteDb-only. This process has several steps, and covers only a small subset of the theoretically
 * possible combinations of {@link NotesMigration} return values.
 *
 * <p>These states are ordered: a one-way migration from ReviewDb to NoteDb will pass through states
 * in the order in which they are defined.
 */
public enum NotesMigrationState {
  REVIEW_DB(false, false, false, PrimaryStorage.REVIEW_DB, false),

  WRITE(false, true, false, PrimaryStorage.REVIEW_DB, false),

  READ_WRITE_NO_SEQUENCE(true, true, false, PrimaryStorage.REVIEW_DB, false),

  READ_WRITE_WITH_SEQUENCE_REVIEW_DB_PRIMARY(true, true, true, PrimaryStorage.REVIEW_DB, false),

  READ_WRITE_WITH_SEQUENCE_NOTE_DB_PRIMARY(true, true, true, PrimaryStorage.NOTE_DB, false),

  NOTE_DB(true, true, true, PrimaryStorage.NOTE_DB, true);

  public static final NotesMigrationState FINAL = NOTE_DB;

  public static Optional<NotesMigrationState> forConfig(Config cfg) {
    return forSnapshot(Snapshot.create(cfg));
  }

  public static Optional<NotesMigrationState> forNotesMigration(NotesMigration migration) {
    return forSnapshot(migration.snapshot());
  }

  private static Optional<NotesMigrationState> forSnapshot(Snapshot s) {
    return Stream.of(values()).filter(v -> v.snapshot.equals(s)).findFirst();
  }

  private final Snapshot snapshot;

  NotesMigrationState(
      // Arguments match abstract methods in NotesMigration.
      boolean readChanges,
      boolean rawWriteChangesSetting,
      boolean readChangeSequence,
      PrimaryStorage changePrimaryStorage,
      boolean disableChangeReviewDb) {
    this.snapshot =
        Snapshot.builder()
            .setReadChanges(readChanges)
            .setWriteChanges(rawWriteChangesSetting)
            .setReadChangeSequence(readChangeSequence)
            .setChangePrimaryStorage(changePrimaryStorage)
            .setDisableChangeReviewDb(disableChangeReviewDb)
            .build();
  }

  public void setConfigValues(Config cfg) {
    snapshot.setConfigValues(cfg);
  }

  public String toText() {
    Config cfg = new Config();
    setConfigValues(cfg);
    return cfg.toText();
  }

  Snapshot snapshot() {
    return snapshot;
  }
}
