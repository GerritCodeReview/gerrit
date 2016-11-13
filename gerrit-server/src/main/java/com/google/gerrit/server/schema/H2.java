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
import java.nio.file.Path;
import org.eclipse.jgit.lib.Config;

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
    return appendUrlOptions(cfg, createUrl(site.resolve(database)));
  }

  public static String createUrl(Path path) {
    return new StringBuilder().append("jdbc:h2:").append(path.toUri().toString()).toString();
  }

  public static String appendUrlOptions(Config cfg, String url) {
    long h2CacheSize = cfg.getLong("database", "h2", "cacheSize", -1);
    boolean h2AutoServer = cfg.getBoolean("database", "h2", "autoServer", false);

    StringBuilder urlBuilder = new StringBuilder().append(url);

    if (h2CacheSize >= 0) {
      // H2 CACHE_SIZE is always given in KB
      urlBuilder.append(";CACHE_SIZE=").append(h2CacheSize / 1024);
    }
    if (h2AutoServer) {
      urlBuilder.append(";AUTO_SERVER=TRUE");
    }
    return urlBuilder.toString();
  }
}
