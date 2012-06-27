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

package com.google.gerrit.server.schema;

import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;

import org.eclipse.jgit.lib.Config;

import java.io.File;
import java.io.IOException;

class H2 extends BaseDataSourceType {

  protected final Config cfg;
  private final SitePaths site;

  @Inject
  H2(final SitePaths site, @GerritServerConfig final Config cfg) {
    super("org.h2.Driver");
    this.cfg = cfg;
    this.site = site;
  }

  @Override
  public String getUrl() {
    String database = cfg.getString("database", null, "database");
    if (database == null || database.isEmpty()) {
      database = "db/ReviewDB";
    }
    File db = site.resolve(database);
    try {
      db = db.getCanonicalFile();
    } catch (IOException e) {
      db = db.getAbsoluteFile();
    }
    return "jdbc:h2:" + db.toURI().toString();
  }
}
