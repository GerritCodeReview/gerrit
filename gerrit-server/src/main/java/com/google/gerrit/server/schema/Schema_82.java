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
import com.google.gwtorm.server.StatementExecutor;
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
    JdbcSchema s = (JdbcSchema) db;
    try (JdbcExecutor e = new JdbcExecutor(s)) {
      renameTables(db, s, e);
      renameColumn(db, s, e);
    }
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
      try (Statement stmt = newStatement(db)) {
        addCheckConstraint(stmt);
      }
    }
  }

  private void renameIndexes(ReviewDb db) {
    SqlDialect dialect = ((JdbcSchema) db).getDialect();
    // Use a new executor so we can ignore errors.
    try (StatementExecutor e = newExecutor(db)) {
      // MySQL doesn't have alter index stmt, drop & create
      if (dialect instanceof DialectMySQL) {
        for (Map.Entry<String, Index> entry : indexes.entrySet()) {
          dialect.dropIndex(e, entry.getValue().table, entry.getKey());
        }
        e.execute("CREATE INDEX account_project_watches_byP ON " +
            "account_project_watches (project_name)");
        e.execute("CREATE INDEX patch_set_approvals_closedByU ON " +
            "patch_set_approvals (change_open, account_id, change_sort_key)");
        e.execute("CREATE INDEX submodule_subscr_acc_bys ON " +
            "submodule_subscriptions (submodule_project_name, " +
            "submodule_branch_name)");
      } else {
        for (Map.Entry<String, Index> entry : indexes.entrySet()) {
          e.execute("ALTER INDEX " + entry.getKey() + " RENAME TO "
              + entry.getValue().index);
        }
      }
    } catch (OrmException e) {
      // We don't care; better, we could check if index was already renamed, but
      // gwtorm didn't expose this functionality at the time this schema upgrade
      // was written.
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
