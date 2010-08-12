// Copyright (C) 2010 The Android Open Source Project
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

import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.gwtorm.schema.sql.DialectMySQL;
import com.google.gwtorm.schema.sql.DialectPostgreSQL;
import com.google.gwtorm.schema.sql.SqlDialect;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class Schema_103 extends SchemaVersion {
  private static final int MAX_SCAN_SIZE = 1000;

  @Inject
  Schema_103(Provider<Schema_41> prior) {
    super(prior);
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws SQLException {
    Statement stmt = ((JdbcSchema) db).getConnection().createStatement();
    final SqlDialect dialect = ((JdbcSchema) db).getDialect();
    stmt.execute("CREATE INDEX changes_upgrade103 ON changes (sort_key_desc)");

    List<ToUpdate> changes = new ArrayList<ToUpdate>(MAX_SCAN_SIZE);

    PreparedStatement selectStmt =
        ((JdbcSchema) db).getConnection().prepareStatement(
            "SELECT change_id, sort_key FROM changes"
                + " WHERE sort_key_desc IS NULL OR sort_key_desc=''");

    selectStmt.setMaxRows(MAX_SCAN_SIZE);

    PreparedStatement updateChangeStmt =
        ((JdbcSchema) db).getConnection().prepareStatement(
            "UPDATE changes SET sort_key_desc = ? WHERE change_id = ?");
    PreparedStatement updateApprovalStmt =
        ((JdbcSchema) db).getConnection().prepareStatement(
            "UPDATE patch_set_approvals SET change_sort_key_desc = ?"
                + " WHERE change_id = ?");

    try {
      while (true) {
        ResultSet rs = selectStmt.executeQuery();
        try {
          while (rs.next() && changes.size() < MAX_SCAN_SIZE) {
            changes.add(new ToUpdate(rs.getInt(1), rs.getString(2)));
          }
        } finally {
          rs.close();
        }

        if (changes.isEmpty()) {
          break;
        }

        int batchSize = 0;
        for (ToUpdate u : changes) {
          String desc = Long.toHexString(-1l - Long.parseLong(u.sortKey, 16));

          updateChangeStmt.setString(1, desc);
          updateChangeStmt.setInt(2, u.id);
          updateChangeStmt.addBatch();

          updateApprovalStmt.setString(1, desc);
          updateApprovalStmt.setInt(2, u.id);
          updateApprovalStmt.addBatch();

          batchSize++;

          if (batchSize >= 200) {
            updateChangeStmt.executeBatch();
            updateApprovalStmt.executeBatch();
            batchSize = 0;
          }
        }
        if (batchSize > 0) {
          updateChangeStmt.executeBatch();
          updateApprovalStmt.executeBatch();
        }

        changes.clear();
      }

      if (((JdbcSchema) db).getDialect() instanceof DialectMySQL) {
        stmt.execute("DROP INDEX changes_upgrade103 ON changes");
      } else {
        stmt.execute("DROP INDEX changes_upgrade103");
      }

      if (dialect instanceof DialectPostgreSQL) {
        stmt.execute("CREATE INDEX changes_allOpenD ON changes (sort_key_desc) WHERE open = 'Y'");
        stmt.execute("CREATE INDEX changes_byProjectOpenD ON changes (dest_project_name, sort_key_desc) WHERE open = 'Y'");
        stmt.execute("CREATE INDEX changes_allClosedD ON changes (status, sort_key_desc) WHERE open = 'N'");
        stmt.execute("CREATE INDEX patch_set_approvals_closedByUserD ON patch_set_approvals (account_id, change_sort_key_desc) WHERE change_open = 'N'");

      } else {
        stmt.execute("CREATE INDEX changes_allOpenD ON changes (open, sort_key_desc)");
        stmt.execute("CREATE INDEX changes_byProjectOpenD ON changes (open, dest_project_name, sort_key_desc)");
        stmt.execute("CREATE INDEX changes_allClosedD ON changes (open, status, sort_key_desc);");
        stmt.execute("CREATE INDEX patch_set_approvals_closedByUserD ON patch_set_approvals (change_open, account_id, change_sort_key_desc)");
      }
    } finally {
      stmt.close();
      updateChangeStmt.close();
      selectStmt.close();
    }
  }

  private static class ToUpdate {
    int id;
    String sortKey;

    ToUpdate(int changeId, String sortKey) {
      this.id = changeId;
      this.sortKey = sortKey;
    }
  }
}
