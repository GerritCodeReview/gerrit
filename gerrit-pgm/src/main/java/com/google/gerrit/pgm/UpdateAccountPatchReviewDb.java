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
import com.google.gerrit.server.schema.JdbcAccountPatchReviewStore;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.kohsuke.args4j.Option;

public class UpdateAccountPatchReviewDb extends BaseAccountPathReviewDbMigration {

  @Option(
    name = "--force",
    aliases = {"-f"},
    usage = "Force update when already updated"
  )
  private boolean force;

  @Override
  protected int migrate() throws SQLException {
    JdbcAccountPatchReviewStore jdbcAccountPatchReviewStore =
        JdbcAccountPatchReviewStore.createAccountPatchReviewStore(
            cfg, sitePaths, threadSettingsConfig);
    try (Connection conn = jdbcAccountPatchReviewStore.getConnection()) {
      conn.setAutoCommit(false);
      Statement stmt = conn.createStatement();
      if (columnCount(stmt) == 4) {
        stmt.executeUpdate("ALTER TABLE account_patch_reviews ADD file_name_sha1 VARCHAR(40)");
      } else if (force) {
        System.out.println("Force updating account_patch_reviews");
      } else {
        System.err.println("account_patch_reviews is already updated");
        return 1;
      }

      PreparedStatement select =
          conn.prepareStatement(
              "SELECT account_id, change_id, patch_set_id, file_name"
                  + " FROM account_patch_reviews"
                  + " LIMIT ?"
                  + " OFFSET ?");

      PreparedStatement update =
          conn.prepareStatement(
              "UPDATE account_patch_reviews"
                  + " SET file_name_sha1 = ?"
                  + " WHERE account_id = ?"
                  + " AND change_id = ?"
                  + " AND patch_set_id = ?"
                  + " AND file_name = ?");

      int offset = 0;
      Stopwatch sw = Stopwatch.createStarted();
      List<Row> rows = selectRows(select, offset);
      while (!rows.isEmpty()) {
        updateRows(conn, update, rows);
        offset += rows.size();
        System.out.printf("%8d rows updated\n", offset);
        rows = selectRows(select, offset);
      }
      double t = sw.elapsed(TimeUnit.MILLISECONDS) / 1000d;
      System.out.printf("Migrated %d rows in %.01fs (%.01f/s)\n", offset, t, offset / t);

      stmt.executeUpdate("ALTER TABLE account_patch_reviews" + " DROP PRIMARY KEY");
      stmt.executeUpdate(
          "ALTER TABLE account_patch_reviews"
              + " ADD PRIMARY KEY (change_id, patch_set_id, account_id, file_name_sha1)");
    }

    return 0;
  }

  private int columnCount(Statement stmt) throws SQLException {
    return stmt.executeQuery("SELECT * FROM account_patch_reviews LIMIT 1")
        .getMetaData()
        .getColumnCount();
  }

  private void updateRows(Connection conn, PreparedStatement update, List<Row> rows)
      throws SQLException {
    for (Row r : rows) {
      update.setString(1, sha1(r.fileName()));
      update.setLong(2, r.accountId());
      update.setLong(3, r.changeId());
      update.setLong(4, r.patchSetId());
      update.setString(5, r.fileName());
      update.addBatch();
    }
    update.executeBatch();
    conn.commit();
  }
}
