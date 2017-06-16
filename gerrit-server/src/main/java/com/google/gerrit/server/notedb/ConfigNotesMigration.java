// Copyright (C) 2013 The Android Open Source Project
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
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.notedb.NoteDbChangeState.PrimaryStorage;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eclipse.jgit.lib.Config;

/**
 * Implement NoteDb migration stages using {@code gerrit.config}.
 *
 * <p>This class controls the state of the migration according to options in {@code gerrit.config}.
 * In general, any changes to these options should only be made by adventurous administrators, who
 * know what they're doing, on non-production data, for the purposes of testing the NoteDb
 * implementation. Changing options quite likely requires re-running {@code MigrateToNoteDb}. For
 * these reasons, the options remain undocumented.
 */
@Singleton
public class ConfigNotesMigration extends NotesMigration {
  public static class Module extends AbstractModule {
    @Override
    public void configure() {
      bind(NotesMigration.class).to(ConfigNotesMigration.class);
    }
  }

  public static final String SECTION_NOTE_DB = "noteDb";

  private static final String DISABLE_REVIEW_DB = "disableReviewDb";
  private static final String FUSE_UPDATES = "fuseUpdates";
  private static final String PRIMARY_STORAGE = "primaryStorage";
  private static final String READ = "read";
  private static final String SEQUENCE = "sequence";
  private static final String WRITE = "write";

  public static Config allEnabledConfig() {
    Config cfg = new Config();
    cfg.setBoolean(SECTION_NOTE_DB, CHANGES.key(), WRITE, true);
    cfg.setBoolean(SECTION_NOTE_DB, CHANGES.key(), READ, true);
    cfg.setBoolean(SECTION_NOTE_DB, CHANGES.key(), SEQUENCE, true);
    cfg.setString(SECTION_NOTE_DB, CHANGES.key(), PRIMARY_STORAGE, PrimaryStorage.NOTE_DB.name());
    cfg.setBoolean(SECTION_NOTE_DB, CHANGES.key(), DISABLE_REVIEW_DB, true);
    // TODO(dborowitz): Set to true when FileRepository supports it.
    cfg.setBoolean(SECTION_NOTE_DB, CHANGES.key(), FUSE_UPDATES, false);
    return cfg;
  }

  public static void setConfigValues(Config cfg, NotesMigration migration) {
    cfg.setBoolean(SECTION_NOTE_DB, CHANGES.key(), WRITE, migration.rawWriteChangesSetting());
    cfg.setBoolean(SECTION_NOTE_DB, CHANGES.key(), READ, migration.readChanges());
    cfg.setBoolean(SECTION_NOTE_DB, CHANGES.key(), SEQUENCE, migration.readChangeSequence());
    cfg.setEnum(SECTION_NOTE_DB, CHANGES.key(), PRIMARY_STORAGE, migration.changePrimaryStorage());
    cfg.setBoolean(
        SECTION_NOTE_DB, CHANGES.key(), DISABLE_REVIEW_DB, migration.disableChangeReviewDb());
    cfg.setBoolean(SECTION_NOTE_DB, CHANGES.key(), FUSE_UPDATES, migration.fuseUpdates());
  }

  public static String toText(NotesMigration migration) {
    Config cfg = new Config();
    setConfigValues(cfg, migration);
    return cfg.toText();
  }

  @AutoValue
  abstract static class Snapshot {
    static Snapshot create(Config cfg) {
      boolean writeChanges = cfg.getBoolean(SECTION_NOTE_DB, CHANGES.key(), WRITE, false);
      boolean readChanges = cfg.getBoolean(SECTION_NOTE_DB, CHANGES.key(), READ, false);

      // Reading change sequence numbers from NoteDb is not the default even if
      // reading changes themselves is. Once this is enabled, it's not easy to
      // undo: ReviewDb might hand out numbers that have already been assigned by
      // NoteDb. This decision for the default may be reevaluated later.
      boolean readChangeSequence = cfg.getBoolean(SECTION_NOTE_DB, CHANGES.key(), SEQUENCE, false);

      PrimaryStorage changePrimaryStorage =
          cfg.getEnum(SECTION_NOTE_DB, CHANGES.key(), PRIMARY_STORAGE, PrimaryStorage.REVIEW_DB);
      boolean disableChangeReviewDb =
          cfg.getBoolean(SECTION_NOTE_DB, CHANGES.key(), DISABLE_REVIEW_DB, false);
      boolean fuseUpdates = cfg.getBoolean(SECTION_NOTE_DB, CHANGES.key(), FUSE_UPDATES, false);

      checkArgument(
          !(disableChangeReviewDb && changePrimaryStorage != PrimaryStorage.NOTE_DB),
          "cannot disable ReviewDb for changes if default change primary storage is ReviewDb");

      return new AutoValue_ConfigNotesMigration_Snapshot(
          writeChanges,
          readChanges,
          readChangeSequence,
          changePrimaryStorage,
          disableChangeReviewDb,
          fuseUpdates);
    }

    abstract boolean writeChanges();

    abstract boolean readChanges();

    abstract boolean readChangeSequence();

    abstract PrimaryStorage changePrimaryStorage();

    abstract boolean disableChangeReviewDb();

    abstract boolean fuseUpdates();
  }

  private volatile Snapshot snapshot;

  @Inject
  public ConfigNotesMigration(@GerritServerConfig Config cfg) {
    this.snapshot = Snapshot.create(cfg);
  }

  @Override
  public boolean rawWriteChangesSetting() {
    return snapshot.writeChanges();
  }

  @Override
  public boolean readChanges() {
    return snapshot.readChanges();
  }

  @Override
  public boolean readChangeSequence() {
    return snapshot.readChangeSequence();
  }

  @Override
  public PrimaryStorage changePrimaryStorage() {
    return snapshot.changePrimaryStorage();
  }

  @Override
  public boolean disableChangeReviewDb() {
    return snapshot.disableChangeReviewDb();
  }

  @Override
  public boolean fuseUpdates() {
    return snapshot.fuseUpdates();
  }

  /**
   * Set the in-memory values returned by this instance to match another instance.
   *
   * <p>This method is only intended for use by {@link
   * com.google.gerrit.server.notedb.rebuild.NoteDbMigrator}.
   *
   * <p>This <em>only</em> modifies the in-memory state; if this instance was initialized from a
   * file-based config, the underlying storage is not updated. Callers are responsible for managing
   * the underlying storage on their own. This method is synchronized to aid in such
   * implementations.
   *
   * @see NotesMigration#setFrom(NotesMigration)
   */
  @Override
  public synchronized ConfigNotesMigration setFrom(NotesMigration other) {
    Config cfg = new Config();
    setConfigValues(cfg, other);
    snapshot = Snapshot.create(cfg);
    return this;
  }
}
