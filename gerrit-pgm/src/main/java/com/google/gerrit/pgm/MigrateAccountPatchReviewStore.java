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

package com.google.gerrit.pgm;

import com.google.auto.value.AutoValue;
import com.google.common.base.Strings;
import com.google.gerrit.pgm.util.SiteProgram;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.schema.DataSourceProvider;
import com.google.gerrit.server.schema.JdbcAccountPatchReviewStore;
import com.google.inject.Injector;
import com.google.inject.Key;

import org.eclipse.jgit.lib.Config;
import org.kohsuke.args4j.Option;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Migrates AccountPatchReviewStore from one to another */
public class MigrateAccountPatchReviewStore extends SiteProgram {

  @Option(name = "--sourceUrl", usage = "Url of source database")
  private String sourceUrl;

  @Option(name = "--chunkSize", usage = "chunk size of fetching from source and push to target on each time")
  private static long chunkSize = 100000;

  @Override
  public int run() throws Exception {
    SitePaths sitePaths = new SitePaths(getSitePath());
    Config fakeCfg = new Config();
    if (!Strings.isNullOrEmpty(sourceUrl)) {
      System.out.println("source Url (custom): " + sourceUrl);
      fakeCfg.setString("accountPatchReviewDb", null, "url", sourceUrl);
    }
    JdbcAccountPatchReviewStore sourceJdbcAccountPatchReviewStore =
        JdbcAccountPatchReviewStore.createAccountPatchReviewStore(fakeCfg,
            sitePaths);

    Injector dbInjector = createDbInjector(DataSourceProvider.Context.SINGLE_USER);
    Config cfg = dbInjector.getInstance(Key.get(Config.class, GerritServerConfig.class));
    String targetUrl = cfg.getString("accountPatchReviewDb", null, "url");
    if (targetUrl == null) {
      System.err.println("accountPatchReviewDb.url is null in gerrit.config");
      return 1;
    }
    System.out.println("target Url: " + targetUrl);
    JdbcAccountPatchReviewStore targetJdbcAccountPatchReviewStore =
        JdbcAccountPatchReviewStore.createAccountPatchReviewStore(cfg, sitePaths);
    targetJdbcAccountPatchReviewStore.createTableIfNotExists();

    if (!isTargetTableEmpty(targetJdbcAccountPatchReviewStore)) {
      System.err.println("target table is not empty, cannot proceed");
      return 1;
    }

    try (Connection sourceCon = sourceJdbcAccountPatchReviewStore.getConnection();
        Connection targetCon = targetJdbcAccountPatchReviewStore.getConnection();
        PreparedStatement sourceStmt =
            sourceCon.prepareStatement(
                "SELECT ACCOUNT_ID, CHANGE_ID, PATCH_SET_ID, FILE_NAME "
                    + "FROM ACCOUNT_PATCH_REVIEWS "
                    + "LIMIT ? "
                    + "OFFSET ?");
        PreparedStatement targetStmt =
            targetCon.prepareStatement("INSERT INTO ACCOUNT_PATCH_REVIEWS "
                + "(ACCOUNT_ID, CHANGE_ID, PATCH_SET_ID, FILE_NAME) VALUES "
                + "(?, ?, ?, ?)")) {
      targetCon.setAutoCommit(false);
      long offset = 0;
      List<Row> rows = selectRows(sourceStmt, offset);
      while (!rows.isEmpty()) {
        insertRows(targetCon, targetStmt, rows);
        offset += rows.size();
        rows = selectRows(sourceStmt, offset);
      }
    }
    return 0;
  }

  private boolean isTargetTableEmpty(JdbcAccountPatchReviewStore store)
      throws SQLException {
    try (Connection con = store.getConnection();
        Statement s = con.createStatement();
        ResultSet r = s.executeQuery(
            "SELECT COUNT(1) FROM ACCOUNT_PATCH_REVIEWS")) {
      if (r.next()) {
        return r.getInt(1) == 0;
      }
      return true;
    }
  }

  @AutoValue
  abstract static class Row {
    abstract int accountId();
    abstract int changeId();
    abstract int patchSetId();
    abstract String fileName();
  }

  private static List<Row> selectRows(PreparedStatement stmt, long offset)
      throws SQLException {
    List<Row> results = new ArrayList<>();
    stmt.setLong(1, chunkSize);
    stmt.setLong(2, offset);
    try (ResultSet rs = stmt.executeQuery()) {
      while (rs.next()) {
        results.add(new AutoValue_MigrateAccountPatchReviewStore_Row(
            rs.getInt("ACCOUNT_ID"),
            rs.getInt("CHANGE_ID"),
            rs.getInt("PATCH_SET_ID"),
            rs.getString("FILE_NAME")));
      }
    }
    return results;
  }

  private static void insertRows(Connection con, PreparedStatement stmt,
      List<Row> rows) throws SQLException {
    for (Row r : rows) {
      stmt.setLong(1, r.accountId());
      stmt.setLong(2, r.changeId());
      stmt.setLong(3, r.patchSetId());
      stmt.setString(4, r.fileName());
      stmt.addBatch();
    }
    stmt.executeBatch();
    con.commit();
  }
}
