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

package com.google.gerrit.httpd;

import com.google.gerrit.pgm.init.BaseInit;
import com.google.gerrit.pgm.init.PluginsDistribution;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SiteInitializer {
  private static final Logger LOG = LoggerFactory.getLogger(SiteInitializer.class);

  private final String sitePath;
  private final String initPath;
  private final PluginsDistribution pluginsDistribution;
  private final List<String> pluginsToInstall;

  SiteInitializer(
      String sitePath,
      String initPath,
      PluginsDistribution pluginsDistribution,
      List<String> pluginsToInstall) {
    this.sitePath = sitePath;
    this.initPath = initPath;
    this.pluginsDistribution = pluginsDistribution;
    this.pluginsToInstall = pluginsToInstall;
  }

  public void init() {
    try {
      if (sitePath != null) {
        Path site = Paths.get(sitePath);
        LOG.info("Initializing site at " + site.toRealPath().normalize());
        new BaseInit(site, false, true, pluginsDistribution, pluginsToInstall).run();
        return;
      }

      try (Connection conn = connectToDb()) {
        Path site = getSiteFromReviewDb(conn);
        if (site == null && initPath != null) {
          site = Paths.get(initPath);
        }
        if (site != null) {
          LOG.info("Initializing site at " + site.toRealPath().normalize());
          new BaseInit(
                  site,
                  new ReviewDbDataSourceProvider(),
                  false,
                  false,
                  pluginsDistribution,
                  pluginsToInstall)
              .run();
        }
      }
    } catch (Exception e) {
      LOG.error("Site init failed", e);
      throw new RuntimeException(e);
    }
  }

  private Connection connectToDb() throws SQLException {
    return new ReviewDbDataSourceProvider().get().getConnection();
  }

  private Path getSiteFromReviewDb(Connection conn) {
    try (Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT site_path FROM system_config")) {
      if (rs.next()) {
        return Paths.get(rs.getString(1));
      }
    } catch (SQLException e) {
      return null;
    }
    return null;
  }
}
