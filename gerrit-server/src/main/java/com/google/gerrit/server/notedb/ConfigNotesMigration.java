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
import static com.google.gerrit.server.notedb.NoteDbTable.ACCOUNTS;
import static com.google.gerrit.server.notedb.NoteDbTable.CHANGES;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashSet;
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

  private static final String NOTE_DB = "noteDb";
  private static final String READ = "read";
  private static final String WRITE = "write";
  private static final String SEQUENCE = "sequence";

  private static void checkConfig(Config cfg) {
    Set<String> keys = new HashSet<>();
    for (NoteDbTable t : NoteDbTable.values()) {
      keys.add(t.key());
    }
    Set<String> allowed = ImmutableSet.of(READ, WRITE, SEQUENCE);
    for (String t : cfg.getSubsections(NOTE_DB)) {
      checkArgument(keys.contains(t.toLowerCase()), "invalid NoteDb table: %s", t);
      for (String key : cfg.getNames(NOTE_DB, t)) {
        checkArgument(allowed.contains(key.toLowerCase()), "invalid NoteDb key: %s.%s", t, key);
      }
    }
  }

  public static Config allEnabledConfig() {
    Config cfg = new Config();
    for (NoteDbTable t : NoteDbTable.values()) {
      cfg.setBoolean(NOTE_DB, t.key(), WRITE, true);
      cfg.setBoolean(NOTE_DB, t.key(), READ, true);
    }
    return cfg;
  }

  private final boolean writeChanges;
  private final boolean readChanges;
  private final boolean readChangeSequence;

  private final boolean writeAccounts;
  private final boolean readAccounts;

  @Inject
  ConfigNotesMigration(@GerritServerConfig Config cfg) {
    checkConfig(cfg);

    writeChanges = cfg.getBoolean(NOTE_DB, CHANGES.key(), WRITE, false);
    readChanges = cfg.getBoolean(NOTE_DB, CHANGES.key(), READ, false);

    // Reading change sequence numbers from NoteDb is not the default even if
    // reading changes themselves is. Once this is enabled, it's not easy to
    // undo: ReviewDb might hand out numbers that have already been assigned by
    // NoteDb. This decision for the default may be reevaluated later.
    readChangeSequence = cfg.getBoolean(NOTE_DB, CHANGES.key(), SEQUENCE, false);

    writeAccounts = cfg.getBoolean(NOTE_DB, ACCOUNTS.key(), WRITE, false);
    readAccounts = cfg.getBoolean(NOTE_DB, ACCOUNTS.key(), READ, false);
  }

  @Override
  protected boolean writeChanges() {
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
  public boolean writeAccounts() {
    return writeAccounts;
  }

  @Override
  public boolean readAccounts() {
    return readAccounts;
  }
}
