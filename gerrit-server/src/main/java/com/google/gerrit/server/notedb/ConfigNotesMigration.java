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

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.notedb.NoteDbChangeState.PrimaryStorage;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Set;
import org.eclipse.jgit.lib.Config;

/**
 * Implement NoteDb migration stages using {@code gerrit.config}.
 *
 * <p>This class controls the state of the migration according to options in {@code gerrit.config}.
 * In general, any changes to these options should only be made by adventurous administrators, who
 * know what they're doing, on non-production data, for the purposes of testing the NoteDb
 * implementation. Changing options quite likely requires re-running {@code RebuildNoteDb}. For
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

  // All of these names must be reflected in the allowed set in checkConfig.
  private static final String DISABLE_REVIEW_DB = "disableReviewDb";
  private static final String PRIMARY_STORAGE = "primaryStorage";
  private static final String READ = "read";
  private static final String SEQUENCE = "sequence";
  private static final String WRITE = "write";

  private static void checkConfig(Config cfg) {
    Set<String> keys = ImmutableSet.of(CHANGES.key());
    Set<String> allowed =
        ImmutableSet.of(
            DISABLE_REVIEW_DB.toLowerCase(),
            PRIMARY_STORAGE.toLowerCase(),
            READ.toLowerCase(),
            WRITE.toLowerCase(),
            SEQUENCE.toLowerCase());
    for (String t : cfg.getSubsections(SECTION_NOTE_DB)) {
      checkArgument(keys.contains(t.toLowerCase()), "invalid NoteDb table: %s", t);
      for (String key : cfg.getNames(SECTION_NOTE_DB, t)) {
        checkArgument(allowed.contains(key.toLowerCase()), "invalid NoteDb key: %s.%s", t, key);
      }
    }
  }

  public static Config allEnabledConfig() {
    Config cfg = new Config();
    cfg.setBoolean(SECTION_NOTE_DB, CHANGES.key(), WRITE, true);
    cfg.setBoolean(SECTION_NOTE_DB, CHANGES.key(), READ, true);
    cfg.setBoolean(SECTION_NOTE_DB, CHANGES.key(), SEQUENCE, true);
    cfg.setString(SECTION_NOTE_DB, CHANGES.key(), PRIMARY_STORAGE, PrimaryStorage.NOTE_DB.name());
    cfg.setBoolean(SECTION_NOTE_DB, CHANGES.key(), DISABLE_REVIEW_DB, true);
    return cfg;
  }

  private final boolean writeChanges;
  private final boolean readChanges;
  private final boolean readChangeSequence;
  private final PrimaryStorage changePrimaryStorage;
  private final boolean disableChangeReviewDb;

  @Inject
  public ConfigNotesMigration(@GerritServerConfig Config cfg) {
    checkConfig(cfg);

    writeChanges = cfg.getBoolean(SECTION_NOTE_DB, CHANGES.key(), WRITE, false);
    readChanges = cfg.getBoolean(SECTION_NOTE_DB, CHANGES.key(), READ, false);

    // Reading change sequence numbers from NoteDb is not the default even if
    // reading changes themselves is. Once this is enabled, it's not easy to
    // undo: ReviewDb might hand out numbers that have already been assigned by
    // NoteDb. This decision for the default may be reevaluated later.
    readChangeSequence = cfg.getBoolean(SECTION_NOTE_DB, CHANGES.key(), SEQUENCE, false);

    changePrimaryStorage =
        cfg.getEnum(SECTION_NOTE_DB, CHANGES.key(), PRIMARY_STORAGE, PrimaryStorage.REVIEW_DB);
    disableChangeReviewDb =
        cfg.getBoolean(SECTION_NOTE_DB, CHANGES.key(), DISABLE_REVIEW_DB, false);

    checkArgument(
        !(disableChangeReviewDb && changePrimaryStorage != PrimaryStorage.NOTE_DB),
        "cannot disable ReviewDb for changes if default change primary storage is ReviewDb");
  }

  @Override
  public boolean rawWriteChangesSetting() {
    return writeChanges;
  }

  @Override
  public boolean readChanges() {
    return readChanges;
  }

  @Override
  public boolean readChangeSequence() {
    return readChangeSequence;
  }

  @Override
  public PrimaryStorage changePrimaryStorage() {
    return changePrimaryStorage;
  }

  @Override
  public boolean disableChangeReviewDb() {
    return disableChangeReviewDb;
  }
}
