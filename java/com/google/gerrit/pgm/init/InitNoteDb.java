// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.pgm.init;

import static com.google.gerrit.server.notedb.NoteDbTable.CHANGES;
import static com.google.gerrit.server.notedb.NotesMigration.SECTION_NOTE_DB;

import com.google.gerrit.pgm.init.api.InitStep;
import com.google.gerrit.pgm.init.api.Section;
import com.google.gerrit.server.notedb.NotesMigrationState;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eclipse.jgit.lib.Config;

/** Initialize the NoteDb in gerrit site. */
@Singleton
class InitNoteDb implements InitStep {

  private final Section noteDbChanges;

  @Inject
  InitNoteDb(Section.Factory sections) {
    this.noteDbChanges = sections.get(SECTION_NOTE_DB, CHANGES.key());
  }

  @Override
  public void run() {
    initNoteDb();
  }

  private void initNoteDb() {
    Config defaultConfig = new Config();
    NotesMigrationState.FINAL.setConfigValues(defaultConfig);
    for (String name : defaultConfig.getNames(SECTION_NOTE_DB, CHANGES.key())) {
      noteDbChanges.set(name, defaultConfig.getString(SECTION_NOTE_DB, CHANGES.key(), name));
    }
  }
}
