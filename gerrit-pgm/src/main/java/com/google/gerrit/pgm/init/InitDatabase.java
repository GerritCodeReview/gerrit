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

import static com.google.gerrit.pgm.init.InitUtil.die;
import static com.google.gerrit.pgm.init.InitUtil.username;
import static com.google.gerrit.server.schema.DataSourceProvider.Type.H2;

import com.google.gerrit.pgm.util.ConsoleUI;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.schema.DataSourceProvider;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.io.File;

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
    this.database = sections.get("database");
  }

  public void run() {
    ui.header("SQL Database");

    final DataSourceProvider.Type db_type =
        database.select("Database server type", "type", H2);

    switch (db_type) {
      case MYSQL:
        libraries.mysqlDriver.downloadRequired();
        break;
    }

    final boolean userPassAuth;
    switch (db_type) {
      case NOSQL_HEAP_FILE:
      case H2: {
        userPassAuth = false;
        String path = database.get("database");
        if (path == null) {
          path = "db/ReviewDB";
          database.set("database", path);
        }
        File db = site.resolve(path);
        if (db == null) {
          throw die("database.database must be supplied for H2");
        }
        db = db.getParentFile();
        if (!db.exists() && !db.mkdirs()) {
          throw die("cannot create database.database " + db.getAbsolutePath());
        }
        break;
      }

      case JDBC: {
        userPassAuth = true;
        database.string("Driver class name", "driver", null);
        database.string("URL", "url", null);
        break;
      }

      case POSTGRESQL:
      case MYSQL: {
        userPassAuth = true;
        final String defPort = "(" + db_type.toString() + " default)";
        database.string("Server hostname", "hostname", "localhost");
        database.string("Server port", "port", defPort, true);
        database.string("Database name", "database", "reviewdb");
        break;
      }

      default:
        throw die("internal bug, database " + db_type + " not supported");
    }

    if (userPassAuth) {
      database.string("Database username", "username", username());
      database.password("username", "password");
    }
  }
}
