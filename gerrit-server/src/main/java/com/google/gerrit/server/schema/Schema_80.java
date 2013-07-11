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
import com.google.gwtorm.schema.sql.DialectH2;
import com.google.gwtorm.schema.sql.DialectMySQL;
import com.google.gwtorm.schema.sql.SqlDialect;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

public class Schema_80 extends SchemaVersion {

  private ImmutableMap<String,String> tables = ImmutableMap.of(
      "account_group_includes_by_uuid", "account_group_by_id",
      "account_group_includes_by_uuid_audit", "account_group_by_id_aud");

  private ImmutableMap<String,Index> indexes = ImmutableMap.of(
      "account_project_watches_byProject",
      new Index("account_project_watches", "account_project_watches_byP"),
      "patch_set_approvals_closedByUser",
      new Index("patch_set_approvals", "patch_set_approvals_closedByU"),
      "submodule_subscription_access_bySubscription",
      new Index("submodule_subscriptions", "submodule_subscr_acc_byS")
      );

  @Inject
  Schema_80(Provider<Schema_79> prior) {
    super(prior);
  }

  @Override
  protected void preUpdateSchema(ReviewDb db) throws OrmException, SQLException {
    final JdbcSchema s = (JdbcSchema) db;
    final JdbcExecutor e = new JdbcExecutor(s);
    renameTables(s, e);
    renameColumn(db, s, e);
    renameIndexes(db);
  }

  private void renameTables(final JdbcSchema s, final JdbcExecutor e)
      throws OrmException {
    for (Map.Entry<String, String> entry : tables.entrySet()) {
      s.renameTable(e, entry.getKey(), entry.getValue());
    }
  }

  private void renameColumn(ReviewDb db, final JdbcSchema s,
      final JdbcExecutor e) throws SQLException, OrmException {
    SqlDialect dialect = ((JdbcSchema) db).getDialect();
    if (dialect instanceof DialectH2) {
      renameColumnH2(db);
    } else {
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
    } finally {
      stmt.close();
    }
  }

  /*
   * H2-Databse has a bug:
   * http://code.google.com/p/h2database/issues/detail?id=485
   * When column has a constraint, that was defined inside
   * create table statement, then it can not be renamed
   * with simple `alter table alter column` statement:
   * databse get corrupted.
   *
   * Use a work around:
   * add destination column
   * add check constraint to the destination column
   * update the data: destination = source
   * drop source column
   *
   */
  private void renameColumnH2(ReviewDb db) throws SQLException {
    Statement stmt = ((JdbcSchema) db).getConnection().createStatement();
    try {
      // add destination column
      stmt.executeUpdate("ALTER TABLE accounts ADD COLUMN "
          + "show_user_in_review CHAR(1) DEFAULT 'N' NOT NULL");
      addCheckConstraint(stmt);
      // update the destination column with values from source column
      stmt.executeUpdate("UPDATE accounts SET "
          + "show_user_in_review = show_username_in_review_category");
      // drop source column
      stmt.executeUpdate("ALTER TABLE accounts DROP COLUMN "
          + "show_username_in_review_category");
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
    Index(String tableName, String indexName) {
      this.table = tableName;
      this.index = indexName;
    }
    String table;
    String index;
  }
}
