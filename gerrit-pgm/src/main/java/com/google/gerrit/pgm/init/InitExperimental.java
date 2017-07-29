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

package com.google.gerrit.pgm.init;

import static com.google.gerrit.server.notedb.NoteDbTable.CHANGES;
import static com.google.gerrit.server.notedb.NotesMigration.SECTION_NOTE_DB;

import com.google.gerrit.pgm.init.api.ConsoleUI;
import com.google.gerrit.pgm.init.api.InitFlags;
import com.google.gerrit.pgm.init.api.InitStep;
import com.google.gerrit.pgm.init.api.Section;
import com.google.gerrit.server.notedb.NotesMigrationState;
import com.google.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jgit.lib.Config;

@Singleton
class InitExperimental implements InitStep {
  private final ConsoleUI ui;
  private final InitFlags flags;
  private final Section noteDbChanges;

  @Inject
  InitExperimental(ConsoleUI ui, InitFlags flags, Section.Factory sections) {
    this.ui = ui;
    this.flags = flags; // Don't grab any flags yet; they aren't initialized until BaseInit#run.
    this.noteDbChanges = sections.get(SECTION_NOTE_DB, CHANGES.key());
  }

  @Override
  public void run() {
    ui.header("Experimental features");
    if (!ui.yesno(false, "Enable any experimental features")) {
      return;
    }

    if (flags.isNew) {
      initNoteDb();
    }
  }

  private void initNoteDb() {
    ui.message(
        "Use experimental NoteDb for change metadata?\n"
            + "  NoteDb is not recommended for production servers."
            + "  Please familiarize yourself with the documentation:\n"
            + "  https://gerrit-review.googlesource.com/Documentation/dev-note-db.html\n");
    if (!ui.yesno(false, "Enable")) {
      return;
    }

    Config defaultConfig = new Config();
    NotesMigrationState.FINAL.setConfigValues(defaultConfig);
    for (String name : defaultConfig.getNames(SECTION_NOTE_DB, CHANGES.key())) {
      noteDbChanges.set(name, defaultConfig.getString(SECTION_NOTE_DB, CHANGES.key(), name));
    }
  }
}
