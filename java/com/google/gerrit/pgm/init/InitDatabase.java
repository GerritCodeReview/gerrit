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
import static com.google.inject.Stage.PRODUCTION;

import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import com.google.gerrit.config.GerritServerIdProvider;
import com.google.gerrit.config.SitePaths;
import com.google.gerrit.pgm.init.api.ConsoleUI;
import com.google.gerrit.pgm.init.api.InitFlags;
import com.google.gerrit.pgm.init.api.InitStep;
import com.google.gerrit.pgm.init.api.Section;
import com.google.gerrit.server.notedb.NotesMigrationState;
import com.google.inject.Binding;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Set;
import org.eclipse.jgit.lib.Config;

/** Initialize the {@code database} configuration section. */
@Singleton
class InitDatabase implements InitStep {
  private final ConsoleUI ui;
  private final SitePaths site;
  private final Libraries libraries;
  private final InitFlags flags;
  private final Section database;
  private final Section idSection;
  private final Section noteDbChanges;

  @Inject
  InitDatabase(
      ConsoleUI ui,
      SitePaths site,
      Libraries libraries,
      InitFlags flags,
      Section.Factory sections) {
    this.ui = ui;
    this.site = site;
    this.libraries = libraries;
    this.flags = flags; // Don't grab any flags yet; they aren't initialized until BaseInit#run.
    this.database = sections.get("database", null);
    this.idSection = sections.get(GerritServerIdProvider.SECTION, null);
    this.noteDbChanges = sections.get(SECTION_NOTE_DB, CHANGES.key());
  }

  @Override
  public void run() {
    initSqlDb();
    if (flags.isNew) {
      initNoteDb();
    }
  }

  private void initSqlDb() {
    ui.header("SQL Database");

    Set<String> allowedValues = Sets.newTreeSet();
    Injector i = Guice.createInjector(PRODUCTION, new DatabaseConfigModule(site));
    List<Binding<DatabaseConfigInitializer>> dbConfigBindings =
        i.findBindingsByType(new TypeLiteral<DatabaseConfigInitializer>() {});
    for (Binding<DatabaseConfigInitializer> binding : dbConfigBindings) {
      Annotation annotation = binding.getKey().getAnnotation();
      if (annotation instanceof Named) {
        allowedValues.add(((Named) annotation).value());
      }
    }

    if (!Strings.isNullOrEmpty(database.get("url"))
        && Strings.isNullOrEmpty(database.get("type"))) {
      database.set("type", "jdbc");
    }

    String dbType = database.select("Database server type", "type", "h2", allowedValues);

    DatabaseConfigInitializer dci =
        i.getInstance(Key.get(DatabaseConfigInitializer.class, Names.named(dbType.toLowerCase())));

    if (dci instanceof MySqlInitializer) {
      libraries.mysqlDriver.downloadRequired();
    } else if (dci instanceof MariaDbInitializer) {
      libraries.mariadbDriver.downloadRequired();
    } else if (dci instanceof OracleInitializer) {
      libraries.oracleDriver.downloadRequired();
    } else if (dci instanceof DB2Initializer) {
      libraries.db2Driver.downloadRequired();
    } else if (dci instanceof HANAInitializer) {
      libraries.hanaDriver.downloadRequired();
    }

    dci.initConfig(database);

    // Initialize UUID for NoteDb on first init.
    String id = idSection.get(GerritServerIdProvider.KEY);
    if (Strings.isNullOrEmpty(id)) {
      idSection.set(GerritServerIdProvider.KEY, GerritServerIdProvider.generate());
    }
  }

  private void initNoteDb() {
    ui.header("NoteDb Database");
    ui.message(
        "Use NoteDb for change metadata?\n"
            + "  See documentation:\n"
            + "  https://gerrit-review.googlesource.com/Documentation/note-db.html\n");
    if (!ui.yesno(true, "Enable")) {
      return;
    }

    Config defaultConfig = new Config();
    NotesMigrationState.FINAL.setConfigValues(defaultConfig);
    for (String name : defaultConfig.getNames(SECTION_NOTE_DB, CHANGES.key())) {
      noteDbChanges.set(name, defaultConfig.getString(SECTION_NOTE_DB, CHANGES.key(), name));
    }
  }
}
