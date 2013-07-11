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

import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gwtorm.jdbc.JdbcExecutor;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.gwtorm.schema.sql.DialectH2;
import com.google.gwtorm.schema.sql.SqlDialect;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.sql.SQLException;
import java.sql.Statement;

public class Schema_80 extends SchemaVersion {

  @Inject
  Schema_80(Provider<Schema_79> prior) {
    super(prior);
  }

  @Override
  protected void preUpdateSchema(ReviewDb db) throws OrmException, SQLException {
    final JdbcSchema s = (JdbcSchema) db;
    final JdbcExecutor e = new JdbcExecutor(s);
    s.renameTable(e, "ACCOUNT_GROUP_INCLUDES_BY_UUID",
        "ACCOUNT_GROUP_BY_ID");
    s.renameTable(e, "ACCOUNT_GROUP_INCLUDES_BY_UUID_AUDIT",
        "ACCOUNT_GROUP_BY_ID_AUD");
    SqlDialect dialect = ((JdbcSchema) db).getDialect();
    if (dialect instanceof DialectH2) {
      renameShowUserInReviewH2(db);
    } else {
      s.renameColumn(e, "ACCOUNTS", "SHOW_USERNAME_IN_REVIEW_CATEGORY",
          "SHOW_USER_IN_REVIEW");
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
   * Using here a work around:
   * add destination column
   * add check constraint to the destination column
   * update the data: destination = source
   * drop source column
   */
  private void renameShowUserInReviewH2(ReviewDb db) throws SQLException {
    Statement stmt = ((JdbcSchema) db).getConnection().createStatement();
    try {
      // add destination column
      stmt.executeUpdate("LTER TABLE ACCOUNTS ADD COLUMN "
          + "SHOW_USER_IN_REVIEW CHAR(1) DEFAULT 'N' NOT NULL");
      // add check constraint for the destination column
      stmt.executeUpdate("ALTER TABLE ACCOUNTS ADD CONSTRAINT "
          + "SHOW_USER_IN_REVIEW_CHECK CHECK "
          + "(SHOW_USER_IN_REVIEW IN('Y', 'N'))");
      // update the destination column with values from source column
      stmt.executeUpdate("UPDATE ACCOUNTS SET "
          + "SHOW_USER_IN_REVIEW = SHOW_USERNAME_IN_REVIEW_CATEGORY");
      // drop source column
      stmt.executeUpdate("ALTER TABLE ACCOUNTS DROP COLUMN "
          + "SHOW_USERNAME_IN_REVIEW_CATEGORY");
    } finally {
      stmt.close();
    }
  }
}
