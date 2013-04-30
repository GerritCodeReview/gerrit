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

import static com.google.inject.Stage.PRODUCTION;

import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import com.google.gerrit.pgm.util.ConsoleUI;
import com.google.gerrit.server.config.SitePaths;
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

/** Initialize the {@code database} configuration section. */
@Singleton
class InitDatabase implements InitStep {
  private final ConsoleUI ui;
  private final SitePaths site;
  private final Libraries libraries;
  private final Section database;

  @Inject
  InitDatabase(final ConsoleUI ui, final SitePaths site, final Libraries libraries,
      final Section.Factory sections) {
    this.ui = ui;
    this.site = site;
    this.libraries = libraries;
    this.database = sections.get("database", null);
  }

  public void run() {
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

    String dbType =
        database.select("Database server type", "type", "h2", allowedValues);

    DatabaseConfigInitializer dci =
        i.getInstance(Key.get(DatabaseConfigInitializer.class,
            Names.named(dbType.toLowerCase())));

    if (dci instanceof MySqlInitializer) {
        libraries.mysqlDriver.downloadRequired();
    }

    dci.initConfig(database);
  }
}
