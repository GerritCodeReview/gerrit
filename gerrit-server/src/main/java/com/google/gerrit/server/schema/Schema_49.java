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
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.gwtorm.schema.sql.DialectH2;
import com.google.gwtorm.schema.sql.DialectMySQL;
import com.google.gwtorm.schema.sql.DialectPostgreSQL;
import com.google.gwtorm.schema.sql.SqlDialect;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.sql.SQLException;
import java.sql.Statement;

public class Schema_49 extends SchemaVersion {
  @Inject
  Schema_49(Provider<Schema_48> prior) {
    super(prior);
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws SQLException,
      OrmException {
    final Statement stmt = ((JdbcSchema) db).getConnection().createStatement();
    try {
      stmt.execute("CREATE INDEX projects_byStatus"
          + " ON projects (status)");

      stmt.execute("UPDATE projects SET last_updated_on = "
          + "(SELECT CURRENT_TIMESTAMP);");

      final SqlDialect dialect = ((JdbcSchema) db).getDialect();
      if ((dialect instanceof DialectPostgreSQL)
          || (dialect instanceof DialectH2)) {
        stmt.execute("UPDATE projects SET status = 'A'"
            + " WHERE name IN (SELECT projects.name FROM projects, changes"
            + " WHERE projects.name = changes.dest_project_name)");

        stmt.execute("UPDATE projects SET status = 'E'"
            + " WHERE name NOT IN (SELECT projects.name FROM projects, changes"
            + " WHERE projects.name = changes.dest_project_name)");
      } else if (dialect instanceof DialectMySQL) {
        stmt.execute("UPDATE projects SET status = 'A'"
            + " WHERE name IN (SELECT DISTINCT changes.dest_project_name"
            + " FROM changes)");

        stmt.execute("UPDATE projects SET status = 'E'"
            + " WHERE name NOT IN (SELECT DISTINCT changes.dest_project_name"
            + " FROM changes)");
      } else {
        throw new OrmException("Unsupported dialect " + dialect);
      }
    } finally {
      stmt.close();
    }
  }
}
