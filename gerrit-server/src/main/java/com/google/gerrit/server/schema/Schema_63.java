// Copyright (C) 2012 The Android Open Source Project
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
import com.google.gwtorm.schema.sql.DialectPostgreSQL;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.sql.SQLException;
import java.sql.Statement;

public class Schema_63 extends SchemaVersion {
  @Inject
  Schema_63(Provider<Schema_62> prior) {
    super(prior);
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws SQLException {
    Statement stmt = ((JdbcSchema) db).getConnection().createStatement();
    try {
      if (((JdbcSchema) db).getDialect() instanceof DialectPostgreSQL) {
        stmt.execute("CREATE INDEX changes_byBranchClosed"
            + " ON changes (status, dest_project_name, dest_branch_name, sort_key)"
            + " WHERE open = 'N'");
      } else {
        stmt.execute("CREATE INDEX changes_byBranchClosed"
            + " ON changes (status, dest_project_name, dest_branch_name, sort_key)");
      }
    } finally {
      stmt.close();
    }
  }
}
