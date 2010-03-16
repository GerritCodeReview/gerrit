// Copyright (C) 2009 The Android Open Source Project
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

import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.SystemConfig;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.gwtorm.schema.sql.DialectH2;
import com.google.gwtorm.schema.sql.DialectMySQL;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;

class Schema_21 extends SchemaVersion {
  @Inject
  Schema_21(Provider<Schema_20> prior) {
    super(prior);
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException, SQLException {
    JdbcSchema jdbc = (JdbcSchema) db;
    SystemConfig sc = db.systemConfig().get(new SystemConfig.Key());

    Statement s = jdbc.getConnection().createStatement();
    try {
      ResultSet r;

      r = s.executeQuery("SELECT name FROM projects WHERE project_id = 0");
      try {
        if (!r.next()) {
          throw new OrmException("Cannot read old wild project");
        }
        sc.wildProjectName = new Project.NameKey(r.getString(1));
      } finally {
        r.close();
      }

      if (jdbc.getDialect() instanceof DialectMySQL) {
        try {
          s.execute("DROP FUNCTION nextval_project_id");
        } catch (SQLException se) {
          ui.message("Warning: could not delete function nextval_project_id");
        }

      } else if (jdbc.getDialect() instanceof DialectH2) {
        s.execute("ALTER TABLE projects DROP CONSTRAINT"
            + " IF EXISTS CONSTRAINT_F3");
      }
    } finally {
      s.close();
    }

    db.systemConfig().update(Collections.singleton(sc));
  }
}
