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

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class Schema_48 extends SchemaVersion {
  @Inject
  Schema_48(Provider<Schema_47> prior) {
    super(prior);
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws SQLException,
      OrmException {
    Statement stmt = null;
    try {
      Connection connection = ((JdbcSchema) db).getConnection();
      stmt = connection.createStatement();

      final SqlDialect dialect = ((JdbcSchema) db).getDialect();
      if (dialect instanceof DialectPostgreSQL) {
        stmt.execute("UPDATE projects SET project_id = nextval('project_id')");
      } else if (dialect instanceof DialectH2) {
        stmt.execute("UPDATE projects SET project_id = nextval('project_id')");
      } else if (dialect instanceof DialectMySQL) {
        stmt.execute("CREATE FUNCTION nextval_project_id () RETURNS BIGINT"
            + " LANGUAGE SQL NOT DETERMINISTIC MODIFIES SQL DATA"
            + " BEGIN INSERT INTO project_id (s) VALUES (NULL);"
            + " DELETE FROM project_id WHERE s = LAST_INSERT_ID();"
            + " RETURN LAST_INSERT_ID(); END");

        stmt.execute("UPDATE projects SET project_id = nextval_project_id()");
      } else {
        throw new OrmException("Unsupported dialect " + dialect);
      }
    } finally {
      if (stmt != null) {
        stmt.close();
      }
    }
  }
}
