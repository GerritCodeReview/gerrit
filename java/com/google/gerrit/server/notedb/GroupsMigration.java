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

import static com.google.gerrit.server.notedb.NoteDbTable.GROUPS;
import static com.google.gerrit.server.notedb.NotesMigration.READ;
import static com.google.gerrit.server.notedb.NotesMigration.SECTION_NOTE_DB;
import static com.google.gerrit.server.notedb.NotesMigration.WRITE;

import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eclipse.jgit.lib.Config;

@Singleton
public class GroupsMigration {
  public static class Module extends AbstractModule {
    @Override
    public void configure() {
      bind(GroupsMigration.class);
    }
  }

  private final boolean writeToNoteDb;
  private final boolean readFromNoteDb;

  @Inject
  public GroupsMigration(@GerritServerConfig Config cfg) {
    // TODO(aliceks): Remove these flags when all other necessary TODOs for writing groups to
    // NoteDb have been addressed.
    // Don't flip these flags in a production setting! We only added them to spread the
    // implementation of groups in NoteDb among several changes which are gradually merged.
    this(
        cfg.getBoolean(SECTION_NOTE_DB, GROUPS.key(), WRITE, false),
        cfg.getBoolean(SECTION_NOTE_DB, GROUPS.key(), READ, false));
  }

  public GroupsMigration(boolean writeToNoteDb, boolean readFromNoteDb) {
    this.writeToNoteDb = writeToNoteDb;
    this.readFromNoteDb = readFromNoteDb;
  }

  public boolean writeToNoteDb() {
    return writeToNoteDb;
  }

  public boolean readFromNoteDb() {
    return readFromNoteDb;
  }

  public void setConfigValues(Config cfg) {
    cfg.setBoolean(SECTION_NOTE_DB, GROUPS.key(), WRITE, writeToNoteDb());
    cfg.setBoolean(SECTION_NOTE_DB, GROUPS.key(), READ, readFromNoteDb());
  }
}
