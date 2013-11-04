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

import com.google.gerrit.pgm.BaseInit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public final class SiteInitializer {
  private static final Logger LOG = LoggerFactory
      .getLogger(SiteInitializer.class);

  private final String sitePath;
  private final String initPath;

  SiteInitializer(String sitePath, String initPath) {
    this.sitePath = sitePath;
    this.initPath = initPath;
  }

  public void init() {
    try {
      if (sitePath != null) {
        File site = new File(sitePath);
        LOG.info(String.format("Initializing site at %s",
            site.getAbsolutePath()));
        new BaseInit(site, false).run();
        return;
      }

      Connection conn = connectToDb();
      try {
        File site = getSiteFromReviewDb(conn);
        if (site == null && initPath != null) {
          site = new File(initPath);
        }
        if (site != null) {
          LOG.info(String.format("Initializing site at %s",
              site.getAbsolutePath()));
          new BaseInit(site, new ReviewDbDataSourceProvider(), false).run();
        }
      } finally {
        conn.close();
      }
    } catch (Exception e) {
      LOG.error("Site init failed", e);
      throw new RuntimeException(e);
    }
  }

  private Connection connectToDb() throws SQLException {
    return new ReviewDbDataSourceProvider().get().getConnection();
  }

  private File getSiteFromReviewDb(Connection conn) {
    try {
      Statement stmt = conn.createStatement();
      try {
        ResultSet rs = stmt.executeQuery("SELECT site_path FROM system_config");
        if (rs.next()) {
          return new File(rs.getString(1));
        }
      } finally {
        stmt.close();
      }
      return null;
    } catch (SQLException e) {
      return null;
    }
  }
}
