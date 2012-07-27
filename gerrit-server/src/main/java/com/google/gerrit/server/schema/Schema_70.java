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
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.sql.SQLException;
import java.sql.Statement;

public class Schema_70 extends SchemaVersion {
  @Inject
  protected Schema_70(Provider<Schema_69> prior) {
    super(prior);
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException,
      SQLException {
    Statement stmt = ((JdbcSchema) db).getConnection().createStatement();
    try {
      stmt.executeUpdate("UPDATE tracking_ids SET tracking_key = tracking_id");
      execute(stmt, "DROP INDEX tracking_ids_byTrkId");
      if (((JdbcSchema) db).getDialect() instanceof DialectPostgreSQL) {
        execute(stmt, "ALTER TABLE tracking_ids DROP CONSTRAINT tracking_ids_pkey");
      } else {
        execute(stmt, "ALTER TABLE tracking_ids DROP PRIMARY KEY");
      }
      stmt.execute("ALTER TABLE tracking_ids"
          + " ADD PRIMARY KEY (change_id, tracking_key, tracking_system)");
      stmt.execute("CREATE INDEX tracking_ids_byTrkKey"
          + " ON tracking_ids (tracking_key)");
    } finally {
      stmt.close();
    }
  }

  private static final void execute(Statement stmt, String command) {
    try {
      stmt.execute(command);
    } catch (SQLException e) {
      // ignore
    }
  }
}
