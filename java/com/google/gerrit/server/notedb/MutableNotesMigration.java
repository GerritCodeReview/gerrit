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

import com.google.gerrit.config.GerritServerConfig;
import com.google.gerrit.server.notedb.NoteDbChangeState.PrimaryStorage;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.function.Function;
import org.eclipse.jgit.lib.Config;

/**
 * {@link NotesMigration} with additional methods for altering the migration state at runtime.
 *
 * <p>Almost all callers care only about inspecting the migration state, and for safety should not
 * have access to mutation methods, which must be used with extreme care. Those callers should
 * inject {@link NotesMigration}.
 *
 * <p>Some callers, namely the NoteDb migration pipeline and tests, do need to alter the migration
 * state at runtime, and those callers are expected to take the necessary precautions such as
 * keeping the in-memory and on-disk config state in sync. Those callers use this class.
 *
 * <p>Mutations to the {@link MutableNotesMigration} are guaranteed to be instantly visible to all
 * callers that use the non-mutable {@link NotesMigration}. The current implementation accomplishes
 * this by always binding {@link NotesMigration} to {@link MutableNotesMigration} in Guice, so there
 * is just one {@link NotesMigration} instance process-wide.
 */
@Singleton
public class MutableNotesMigration extends NotesMigration {
  public static MutableNotesMigration newDisabled() {
    return new MutableNotesMigration(new Config());
  }

  public static MutableNotesMigration fromConfig(Config cfg) {
    return new MutableNotesMigration(cfg);
  }

  @Inject
  MutableNotesMigration(@GerritServerConfig Config cfg) {
    super(Snapshot.create(cfg));
  }

  public MutableNotesMigration setReadChanges(boolean readChanges) {
    return set(b -> b.setReadChanges(readChanges));
  }

  public MutableNotesMigration setWriteChanges(boolean writeChanges) {
    return set(b -> b.setWriteChanges(writeChanges));
  }

  public MutableNotesMigration setReadChangeSequence(boolean readChangeSequence) {
    return set(b -> b.setReadChangeSequence(readChangeSequence));
  }

  public MutableNotesMigration setChangePrimaryStorage(PrimaryStorage changePrimaryStorage) {
    return set(b -> b.setChangePrimaryStorage(changePrimaryStorage));
  }

  public MutableNotesMigration setDisableChangeReviewDb(boolean disableChangeReviewDb) {
    return set(b -> b.setDisableChangeReviewDb(disableChangeReviewDb));
  }

  public MutableNotesMigration setFailOnLoadForTest(boolean failOnLoadForTest) {
    return set(b -> b.setFailOnLoadForTest(failOnLoadForTest));
  }

  /**
   * Set the in-memory values returned by this instance to match the given state.
   *
   * <p>This method is only intended for use by {@link
   * com.google.gerrit.server.notedb.rebuild.NoteDbMigrator}.
   *
   * <p>This <em>only</em> modifies the in-memory state; if this instance was initialized from a
   * file-based config, the underlying storage is not updated. Callers are responsible for managing
   * the underlying storage on their own.
   */
  public MutableNotesMigration setFrom(NotesMigrationState state) {
    snapshot.set(state.snapshot());
    return this;
  }

  /** @see #setFrom(NotesMigrationState) */
  public MutableNotesMigration setFrom(NotesMigration other) {
    snapshot.set(other.snapshot.get());
    return this;
  }

  private MutableNotesMigration set(Function<Snapshot.Builder, Snapshot.Builder> f) {
    snapshot.updateAndGet(s -> f.apply(s.toBuilder()).build());
    return this;
  }
}
