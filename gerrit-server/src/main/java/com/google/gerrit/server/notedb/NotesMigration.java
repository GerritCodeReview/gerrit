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
 * Holds the current state of the notes DB migration.
 * <p>
 * During a transitional period, different subsets of the former gwtorm DB
 * functionality may be enabled on the site, possibly only for reading or
 * writing.
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
      keys.add(t.name().toLowerCase());
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

  public boolean writeChanges() {
    return writeChanges;
  }

  public boolean readChanges() {
    return readChanges;
  }
}
