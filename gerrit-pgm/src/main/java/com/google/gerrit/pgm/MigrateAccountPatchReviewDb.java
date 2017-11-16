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

import static com.google.gerrit.server.schema.JdbcAccountPatchReviewStore.sha1;

import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import com.google.gerrit.server.schema.JdbcAccountPatchReviewStore;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.Config;
import org.kohsuke.args4j.Option;

/** Migrates AccountPatchReviewDb from one to another */
public class MigrateAccountPatchReviewDb extends BaseAccountPathReviewDbMigration {

  @Option(name = "--sourceUrl", usage = "Url of source database")
  private String sourceUrl;

  @Override
  protected int migrate() throws Exception {
    Config fakeCfg = new Config();
    if (!Strings.isNullOrEmpty(sourceUrl)) {
      fakeCfg.setString("accountPatchReviewDb", null, "url", sourceUrl);
    }
    JdbcAccountPatchReviewStore sourceJdbcAccountPatchReviewStore =
        JdbcAccountPatchReviewStore.createAccountPatchReviewStore(
            fakeCfg, sitePaths, threadSettingsConfig);

    String targetUrl = cfg.getString("accountPatchReviewDb", null, "url");
    if (targetUrl == null) {
      System.err.println("accountPatchReviewDb.url is null in gerrit.config");
      return 1;
    }
    System.out.println("target Url: " + targetUrl);
    JdbcAccountPatchReviewStore targetJdbcAccountPatchReviewStore =
        JdbcAccountPatchReviewStore.createAccountPatchReviewStore(
            cfg, sitePaths, threadSettingsConfig);
    targetJdbcAccountPatchReviewStore.createTableIfNotExists();

    if (!isTargetTableEmpty(targetJdbcAccountPatchReviewStore)) {
      System.err.println("target table is not empty, cannot proceed");
      return 1;
    }

    try (Connection sourceCon = sourceJdbcAccountPatchReviewStore.getConnection();
        Connection targetCon = targetJdbcAccountPatchReviewStore.getConnection();
        PreparedStatement sourceStmt =
            sourceCon.prepareStatement(
                "SELECT account_id, change_id, patch_set_id, file_name "
                    + "FROM account_patch_reviews "
                    + "LIMIT ? "
                    + "OFFSET ?");
        PreparedStatement targetStmt =
            targetCon.prepareStatement(
                "INSERT INTO account_patch_reviews "
                    + "(account_id, change_id, patch_set_id, file_name_sha1, file_name) VALUES "
                    + "(?, ?, ?, ?, ?)")) {
      targetCon.setAutoCommit(false);
      long offset = 0;
      Stopwatch sw = Stopwatch.createStarted();
      List<Row> rows = selectRows(sourceStmt, offset);
      while (!rows.isEmpty()) {
        insertRows(targetCon, targetStmt, rows);
        offset += rows.size();
        System.out.printf("%8d rows migrated\n", offset);
        rows = selectRows(sourceStmt, offset);
      }
      double t = sw.elapsed(TimeUnit.MILLISECONDS) / 1000d;
      System.out.printf("Migrated %d rows in %.01fs (%.01f/s)\n", offset, t, offset / t);
    }
    return 0;
  }

  private static boolean isTargetTableEmpty(JdbcAccountPatchReviewStore store) throws SQLException {
    try (Connection con = store.getConnection();
        Statement s = con.createStatement();
        ResultSet r = s.executeQuery("SELECT COUNT(1) FROM account_patch_reviews")) {
      if (r.next()) {
        return r.getInt(1) == 0;
      }
      return true;
    }
  }

  private static void insertRows(Connection con, PreparedStatement stmt, List<Row> rows)
      throws SQLException {
    for (Row r : rows) {
      stmt.setLong(1, r.accountId());
      stmt.setLong(2, r.changeId());
      stmt.setLong(3, r.patchSetId());
      stmt.setString(4, sha1(r.fileName()));
      stmt.setString(5, r.fileName());
      stmt.addBatch();
    }
    stmt.executeBatch();
    con.commit();
  }
}
