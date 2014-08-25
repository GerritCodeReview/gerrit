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

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;

public class Schema_99 extends SchemaVersion {
  @Inject
  Schema_99(Provider<Schema_98> prior) {
    super(prior);
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws SQLException {
    ui.message("Add a column download_mirror to the user preferences");
    Connection conn = ((JdbcSchema) db).getConnection();
    Statement stmt = conn.createStatement();
    SqlDialect dialect = ((JdbcSchema) db).getDialect();
    final Set<String> existingColumns = dialect.listColumns(conn, "accounts");
    // Does target column exist?
    if (existingColumns.contains("download_mirror")) {
      return;
    }

    try {
      stmt.executeUpdate("ALTER TABLE accounts ADD download_mirror varchar(200)");
    } finally {
      stmt.close();
    }
  }
}
