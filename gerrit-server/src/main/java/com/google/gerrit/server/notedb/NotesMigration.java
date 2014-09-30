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

import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.Config;

import java.util.HashSet;
import java.util.Set;

/**
 * Holds the current state of the NoteDb migration.
 * <p>
 * The migration will proceed one root entity type at a time. A <em>root
 * entity</em> is an entity stored in ReviewDb whose key's
 * {@code getParentKey()} method returns null. For an example of the entity
 * hierarchy rooted at Change, see the diagram in
 * {@code com.google.gerrit.reviewdb.client.Change}.
 * <p>
 * During a transitional period, each root entity group from ReviewDb may be
 * either <em>written to</em> or <em>both written to and read from</em> NoteDb.
 * <p>
 * This class controls the state of the migration according to options in
 * {@code gerrit.config}. In general, any changes to these options should only
 * be made by adventurous administrators, who know what they're doing, on
 * non-production data, for the purposes of testing the NoteDb implementation.
 * Changing options quite likely requires re-running {@code RebuildNoteDb}. For
 * these reasons, the options remain undocumented.
 */
@Singleton
public class NotesMigration {
  private static enum Table {
    CHANGES;

    private String key() {
      return name().toLowerCase();
    }
  }

  private static final String NOTEDB = "notedb";
  private static final String READ = "read";
  private static final String WRITE = "write";

  private static void checkConfig(Config cfg) {
    Set<String> keys = new HashSet<>();
    for (Table t : Table.values()) {
      keys.add(t.key());
    }
    for (String t : cfg.getSubsections(NOTEDB)) {
      checkArgument(keys.contains(t.toLowerCase()),
          "invalid notedb table: %s", t);
      for (String key : cfg.getNames(NOTEDB, t)) {
        String lk = key.toLowerCase();
        checkArgument(lk.equals(WRITE) || lk.equals(READ),
            "invalid notedb key: %s.%s", t, key);
      }
      boolean write = cfg.getBoolean(NOTEDB, t, WRITE, false);
      boolean read = cfg.getBoolean(NOTEDB, t, READ, false);
      checkArgument(!(read && !write),
          "must have write enabled when read enabled: %s", t);
    }
  }

  public static NotesMigration allEnabled() {
    return new NotesMigration(allEnabledConfig());
  }

  public static Config allEnabledConfig() {
    Config cfg = new Config();
    for (Table t : Table.values()) {
      cfg.setBoolean(NOTEDB, t.key(), WRITE, true);
      cfg.setBoolean(NOTEDB, t.key(), READ, true);
    }
    return cfg;
  }

  private final boolean writeChanges;
  private final boolean readChanges;

  @Inject
  NotesMigration(@GerritServerConfig Config cfg) {
    checkConfig(cfg);
    writeChanges = cfg.getBoolean(NOTEDB, Table.CHANGES.key(), WRITE, false);
    readChanges = cfg.getBoolean(NOTEDB, Table.CHANGES.key(), READ, false);
  }

  public boolean enabled() {
    return writeChanges()
        || readChanges();
  }

  public boolean writeChanges() {
    return writeChanges;
  }

  public boolean readChanges() {
    return readChanges;
  }
}
