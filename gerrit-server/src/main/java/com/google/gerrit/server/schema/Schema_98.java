// Copyright (C) 2014 The Android Open Source Project
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
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.gwtorm.schema.sql.SqlDialect;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;

public class Schema_98 extends SchemaVersion {
  @Inject
  Schema_98(Provider<Schema_97> prior) {
    super(prior);
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws SQLException {
    ui.message("Migrate user preference showUserInReview to "
        + "reviewCategoryStrategy");
    JdbcSchema s = (JdbcSchema) db;
    Statement stmt = s.getConnection().createStatement();
    SqlDialect dialect = s.getDialect();
    try {
      String showUserReviewColumn;
      Set<String> columns = dialect.listColumns(s.getConnection(), "accounts");
      if (columns.contains("show_user_in_review")) {
        showUserReviewColumn = "show_user_in_review";
      } else if (columns.contains("show_username_in_review_category")) {
        showUserReviewColumn = "show_username_in_review_category";
      } else {
        throw new SQLException("cannot pre-populate reviewCategoryStrategy");
      }

      stmt.executeUpdate("UPDATE accounts SET "
          + "review_category_strategy='NAME' "
          + "WHERE (" + showUserReviewColumn + "='Y')");
    } finally {
      stmt.close();
    }
  }
}
