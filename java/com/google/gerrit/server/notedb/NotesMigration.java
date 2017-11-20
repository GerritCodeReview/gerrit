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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.gerrit.server.notedb.NoteDbTable.CHANGES;

import com.google.auto.value.AutoValue;
import com.google.gerrit.server.notedb.NoteDbChangeState.PrimaryStorage;
import com.google.inject.AbstractModule;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.jgit.lib.Config;

/**
 * Current low-level settings of the NoteDb migration for changes.
 *
 * <p>This class only describes the migration state of the {@link
 * com.google.gerrit.reviewdb.client.Change Change} entity group, since it is possible for a given
 * site to be in different states of the Change NoteDb migration process while staying at the same
 * ReviewDb schema version. It does <em>not</em> describe the migration state of non-Change tables;
 * those are automatically migrated using the ReviewDb schema migration process, so the NoteDb
 * migration state at a given ReviewDb schema cannot vary.
 *
 * <p>In many places, core Gerrit code should not directly care about the NoteDb migration state,
 * and should prefer high-level APIs like {@link com.google.gerrit.server.ApprovalsUtil
 * ApprovalsUtil} that don't require callers to inspect the migration state. The
 * <em>implementation</em> of those utilities does care about the state, and should query the {@code
 * NotesMigration} for the properties of the migration, for example, {@link #changePrimaryStorage()
 * where new changes should be stored}.
 *
 * <p>Core Gerrit code is mostly interested in one facet of the migration at a time (reading or
 * writing, say), but not all combinations of return values are supported or even make sense.
 *
 * <p>This class controls the state of the migration according to options in {@code gerrit.config}.
 * In general, any changes to these options should only be made by adventurous administrators, who
 * know what they're doing, on non-production data, for the purposes of testing the NoteDb
 * implementation. Changing options quite likely requires re-running {@code MigrateToNoteDb}. For
 * these reasons, the options remain undocumented.
 *
 * <p><strong>Note:</strong> Callers should not assume the values returned by {@code
 * NotesMigration}'s methods will not change in a running server.
 */
public abstract class NotesMigration {
  public static final String SECTION_NOTE_DB = "noteDb";
  public static final String READ = "read";
  public static final String WRITE = "write";
  public static final String DISABLE_REVIEW_DB = "disableReviewDb";

  private static final String PRIMARY_STORAGE = "primaryStorage";
  private static final String SEQUENCE = "sequence";

  public static class Module extends AbstractModule {
    @Override
    public void configure() {
      bind(MutableNotesMigration.class);
      bind(NotesMigration.class).to(MutableNotesMigration.class);
    }
  }

  @AutoValue
  abstract static class Snapshot {
    static Builder builder() {
      // Default values are defined as what we would read from an empty config.
      return create(new Config()).toBuilder();
    }

    static Snapshot create(Config cfg) {
      return new AutoValue_NotesMigration_Snapshot.Builder()
          .setWriteChanges(cfg.getBoolean(SECTION_NOTE_DB, CHANGES.key(), WRITE, false))
          .setReadChanges(cfg.getBoolean(SECTION_NOTE_DB, CHANGES.key(), READ, false))
          .setReadChangeSequence(cfg.getBoolean(SECTION_NOTE_DB, CHANGES.key(), SEQUENCE, false))
          .setChangePrimaryStorage(
              cfg.getEnum(
                  SECTION_NOTE_DB, CHANGES.key(), PRIMARY_STORAGE, PrimaryStorage.REVIEW_DB))
          .setDisableChangeReviewDb(
              cfg.getBoolean(SECTION_NOTE_DB, CHANGES.key(), DISABLE_REVIEW_DB, false))
          .setFailOnLoadForTest(false) // Only set in tests, can't be set via config.
          .build();
    }

    abstract boolean writeChanges();

    abstract boolean readChanges();

    abstract boolean readChangeSequence();

    abstract PrimaryStorage changePrimaryStorage();

    abstract boolean disableChangeReviewDb();

    abstract boolean failOnLoadForTest();

    abstract Builder toBuilder();

    void setConfigValues(Config cfg) {
      cfg.setBoolean(SECTION_NOTE_DB, CHANGES.key(), WRITE, writeChanges());
      cfg.setBoolean(SECTION_NOTE_DB, CHANGES.key(), READ, readChanges());
      cfg.setBoolean(SECTION_NOTE_DB, CHANGES.key(), SEQUENCE, readChangeSequence());
      cfg.setEnum(SECTION_NOTE_DB, CHANGES.key(), PRIMARY_STORAGE, changePrimaryStorage());
      cfg.setBoolean(SECTION_NOTE_DB, CHANGES.key(), DISABLE_REVIEW_DB, disableChangeReviewDb());
    }

    @AutoValue.Builder
    abstract static class Builder {
      abstract Builder setWriteChanges(boolean writeChanges);

      abstract Builder setReadChanges(boolean readChanges);

      abstract Builder setReadChangeSequence(boolean readChangeSequence);

      abstract Builder setChangePrimaryStorage(PrimaryStorage changePrimaryStorage);

      abstract Builder setDisableChangeReviewDb(boolean disableChangeReviewDb);

      abstract Builder setFailOnLoadForTest(boolean failOnLoadForTest);

      abstract Snapshot autoBuild();

      Snapshot build() {
        Snapshot s = autoBuild();
        checkArgument(
            !(s.disableChangeReviewDb() && s.changePrimaryStorage() != PrimaryStorage.NOTE_DB),
            "cannot disable ReviewDb for changes if default change primary storage is ReviewDb");
        return s;
      }
    }
  }

  protected final AtomicReference<Snapshot> snapshot;

  /**
   * Read changes from NoteDb.
   *
   * <p>Change data is read from NoteDb refs, but ReviewDb is still the source of truth. If the
   * loader determines NoteDb is out of date, the change data in NoteDb will be transparently
   * rebuilt. This means that some code paths that look read-only may in fact attempt to write.
   *
   * <p>If true and {@code writeChanges() = false}, changes can still be read from NoteDb, but any
   * attempts to write will generate an error.
   */
  public final boolean readChanges() {
    return snapshot.get().readChanges();
  }

  /**
   * Write changes to NoteDb.
   *
   * <p>This method is awkwardly named because you should be using either {@link
   * #commitChangeWrites()} or {@link #failChangeWrites()} instead.
   *
   * <p>Updates to change data are written to NoteDb refs, but ReviewDb is still the source of
   * truth. Change data will not be written unless the NoteDb refs are already up to date, and the
   * write path will attempt to rebuild the change if not.
   *
   * <p>If false, the behavior when attempting to write depends on {@code readChanges()}. If {@code
   * readChanges() = false}, writes to NoteDb are simply ignored; if {@code true}, any attempts to
   * write will generate an error.
   */
  public final boolean rawWriteChangesSetting() {
    return snapshot.get().writeChanges();
  }

  /**
   * Read sequential change ID numbers from NoteDb.
   *
   * <p>If true, change IDs are read from {@code refs/sequences/changes} in All-Projects. If false,
   * change IDs are read from ReviewDb's native sequences.
   */
  public final boolean readChangeSequence() {
    return snapshot.get().readChangeSequence();
  }

  /** @return default primary storage for new changes. */
  public final PrimaryStorage changePrimaryStorage() {
    return snapshot.get().changePrimaryStorage();
  }

  /**
   * Disable ReviewDb access for changes.
   *
   * <p>When set, ReviewDb operations involving the Changes table become no-ops. Lookups return no
   * results; updates do nothing, as does opening, committing, or rolling back a transaction on the
   * Changes table.
   */
  public final boolean disableChangeReviewDb() {
    return snapshot.get().disableChangeReviewDb();
  }

  /**
   * Whether to fail when reading any data from NoteDb.
   *
   * <p>Used in conjunction with {@link #readChanges()} for tests.
   */
  public boolean failOnLoadForTest() {
    return snapshot.get().failOnLoadForTest();
  }

  public final boolean commitChangeWrites() {
    // It may seem odd that readChanges() without writeChanges() means we should
    // attempt to commit writes. However, this method is used by callers to know
    // whether or not they should short-circuit and skip attempting to read or
    // write NoteDb refs.
    //
    // It is possible for commitChangeWrites() to return true and
    // failChangeWrites() to also return true, causing an error later in the
    // same codepath. This specific condition is used by the auto-rebuilding
    // path to rebuild a change and stage the results, but not commit them due
    // to failChangeWrites().
    return rawWriteChangesSetting() || readChanges();
  }

  public final boolean failChangeWrites() {
    return !rawWriteChangesSetting() && readChanges();
  }

  public final void setConfigValues(Config cfg) {
    snapshot.get().setConfigValues(cfg);
  }

  @Override
  public final boolean equals(Object o) {
    return o instanceof NotesMigration
        && snapshot.get().equals(((NotesMigration) o).snapshot.get());
  }

  @Override
  public final int hashCode() {
    return snapshot.get().hashCode();
  }

  protected NotesMigration(Snapshot snapshot) {
    this.snapshot = new AtomicReference<>(snapshot);
  }

  final Snapshot snapshot() {
    return snapshot.get();
  }
}
