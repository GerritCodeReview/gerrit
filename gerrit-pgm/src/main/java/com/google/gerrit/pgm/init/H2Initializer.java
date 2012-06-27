// Copyright (C) 2012 The Android Open Source Project
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

import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;

import java.io.File;

class H2Initializer implements DatabaseConfigInitializer {

  private final SitePaths site;

  @Inject
  H2Initializer(final SitePaths site) {
    this.site = site;
  }

  @Override
  public void initConfig(Section databaseSection) {
    String path = databaseSection.get("database");
    if (path == null) {
      path = "db/ReviewDB";
      databaseSection.set("database", path);
    }
    File db = site.resolve(path);
    if (db == null) {
      throw InitUtil.die("database.database must be supplied for H2");
    }
    db = db.getParentFile();
    if (!db.exists() && !db.mkdirs()) {
      throw InitUtil.die("cannot create database.database "
          + db.getAbsolutePath());
    }
  }
}