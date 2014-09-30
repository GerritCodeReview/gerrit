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

package com.google.gerrit.server.schema;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gwtorm.jdbc.JdbcExecutor;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.gwtorm.schema.sql.DialectMySQL;
import com.google.gwtorm.schema.sql.SqlDialect;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.Set;

public class Schema_82 extends SchemaVersion {

  private Map<String,String> tables = ImmutableMap.of(
      "account_group_includes_by_uuid", "account_group_by_id",
      "account_group_includes_by_uuid_audit", "account_group_by_id_aud");

  private Map<String,Index> indexes = ImmutableMap.of(
      "account_project_watches_byProject",
      new Index("account_project_watches", "account_project_watches_byP"),
      "patch_set_approvals_closedByUser",
      new Index("patch_set_approvals", "patch_set_approvals_closedByU"),
      "submodule_subscription_access_bySubscription",
      new Index("submodule_subscriptions", "submodule_subscr_acc_byS")
      );

  @Inject
  Schema_82(Provider<Schema_81> prior) {
    super(prior);
  }

  @Override
  protected void preUpdateSchema(ReviewDb db) throws OrmException, SQLException {
    final JdbcSchema s = (JdbcSchema) db;
    final JdbcExecutor e = new JdbcExecutor(s);
    renameTables(db, s, e);
    renameColumn(db, s, e);
    renameIndexes(db);
  }

  private void renameTables(final ReviewDb db, final JdbcSchema s,
      final JdbcExecutor e) throws OrmException, SQLException {
    SqlDialect dialect = ((JdbcSchema) db).getDialect();
    final Set<String> existingTables = dialect.listTables(s.getConnection());
    for (Map.Entry<String, String> entry : tables.entrySet()) {
      // Does source table exist?
      if (existingTables.contains(entry.getKey())) {
        // Does target table exist?
        if (!existingTables.contains(entry.getValue())) {
          s.renameTable(e, entry.getKey(), entry.getValue());
        }
      }
    }
  }

  private void renameColumn(final ReviewDb db, final JdbcSchema s,
      final JdbcExecutor e) throws SQLException, OrmException {
    SqlDialect dialect = ((JdbcSchema) db).getDialect();
    final Set<String> existingColumns =
        dialect.listColumns(s.getConnection(), "accounts");
    // Does source column exist?
    if (!existingColumns.contains("show_username_in_review_category")) {
      return;
    }
    // Does target column exist?
    if (existingColumns.contains("show_user_in_review")) {
      return;
    }
    s.renameColumn(e, "accounts", "show_username_in_review_category",
        "show_user_in_review");
    // MySQL loose check constraint during the column renaming.
    // Well it doesn't implemented anyway,
    // check constraints are get parsed but do nothing
    if (dialect instanceof DialectMySQL) {
      Statement stmt = ((JdbcSchema) db).getConnection().createStatement();
      try {
        addCheckConstraint(stmt);
      } finally {
        stmt.close();
      }
    }
  }

  private void renameIndexes(ReviewDb db) throws SQLException {
    SqlDialect dialect = ((JdbcSchema) db).getDialect();
    Statement stmt = ((JdbcSchema) db).getConnection().createStatement();
    try {
      // MySQL doesn't have alter index stmt, drop & create
      if (dialect instanceof DialectMySQL) {
        for (Map.Entry<String, Index> entry : indexes.entrySet()) {
          stmt.executeUpdate("DROP INDEX " + entry.getKey() + " ON "
              + entry.getValue().table);
        }
        stmt.executeUpdate("CREATE INDEX account_project_watches_byP ON " +
            "account_project_watches (project_name)");
        stmt.executeUpdate("CREATE INDEX patch_set_approvals_closedByU ON " +
            "patch_set_approvals (change_open, account_id, change_sort_key)");
        stmt.executeUpdate("CREATE INDEX submodule_subscr_acc_bys ON " +
            "submodule_subscriptions (submodule_project_name, " +
            "submodule_branch_name)");
      } else {
        for (Map.Entry<String, Index> entry : indexes.entrySet()) {
          stmt.executeUpdate("ALTER INDEX " + entry.getKey() + " RENAME TO "
              + entry.getValue().index);
        }
      }
    } catch (SQLException e) {
      // we don't care
      // better we would check if index was already renamed
      // gwtorm doesn't expose this functionality
    } finally {
      stmt.close();
    }
  }

  private void addCheckConstraint(Statement stmt) throws SQLException {
    // add check constraint for the destination column
    stmt.executeUpdate("ALTER TABLE accounts ADD CONSTRAINT "
        + "show_user_in_review_check CHECK "
        + "(show_user_in_review IN('Y', 'N'))");
  }

  static class Index {
    String table;
    String index;

    Index(String tableName, String indexName) {
      this.table = tableName;
      this.index = indexName;
    }
  }
}
