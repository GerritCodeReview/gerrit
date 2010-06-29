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

import com.google.gerrit.reviewdb.AccountProjectWatch;
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

public class Schema_36 extends SchemaVersion {
  @Inject
  Schema_36(Provider<Schema_35> prior) {
    super(prior);
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws SQLException,
      OrmException {
    // Setting to "*" the regex field of the previously watched projects
    //
    Statement stmt = ((JdbcSchema) db).getConnection().createStatement();
    try {
      stmt.execute(String.format(
          "UPDATE account_project_watches SET file_match_regex = '%s'"
              + " WHERE file_match_regex IS NULL OR file_match_regex = ''",
          AccountProjectWatch.DEFAULT_REGEX));

      // Setting the new primary key set
      final SqlDialect dialect = ((JdbcSchema) db).getDialect();
      if (dialect instanceof DialectPostgreSQL) {
        stmt.execute("ALTER TABLE account_project_watches " +
                     "DROP CONSTRAINT account_project_watches_pkey");
        stmt.execute("ALTER TABLE account_project_watches " +
                     "ADD PRIMARY KEY (account_id, project_name, file_match_regex)");
      } else if ((dialect instanceof DialectH2)
          || (dialect instanceof DialectMySQL)) {
        stmt.execute("ALTER TABLE account_project_watches " +
                     "DROP PRIMARY KEY");
        stmt.execute("ALTER TABLE account_project_watches " +
                     "ADD PRIMARY KEY (account_id, project_name, file_match_regex)");
      } else {
        throw new OrmException("Unsupported dialect " + dialect);
      }
    } finally {
      stmt.close();
    }
  }

}
