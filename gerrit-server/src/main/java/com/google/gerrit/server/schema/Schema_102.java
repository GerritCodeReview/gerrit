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
import com.google.gwtorm.schema.sql.DialectPostgreSQL;
import com.google.gwtorm.schema.sql.SqlDialect;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.StatementExecutor;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.sql.SQLException;
import java.util.Set;
import java.util.regex.Pattern;

public class Schema_102 extends SchemaVersion {
  @Inject
  Schema_102(Provider<Schema_101> prior) {
    super(prior);
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException, SQLException {
    JdbcSchema schema = (JdbcSchema) db;
    SqlDialect dialect = schema.getDialect();
    try (StatementExecutor e = newExecutor(db)) {
      // Drop left over indexes that were missed to be removed in schema 84.
      // See "Delete SQL index support" commit for more details:
      // d4ae3a16d5e1464574bd04f429a63eb9c02b3b43
      Pattern pattern =
          Pattern.compile("^changes_(allOpen|allClosed|byBranchClosed)$", Pattern.CASE_INSENSITIVE);
      String table = "changes";
      Set<String> listIndexes = dialect.listIndexes(schema.getConnection(), table);
      for (String index : listIndexes) {
        if (pattern.matcher(index).matches()) {
          dialect.dropIndex(e, table, index);
        }
      }

      dialect.dropIndex(e, table, "changes_byProjectOpen");
      if (dialect instanceof DialectPostgreSQL) {
        e.execute(
            "CREATE INDEX changes_byProjectOpen"
                + " ON "
                + table
                + " (dest_project_name, last_updated_on)"
                + " WHERE open = 'Y'");
      } else {
        e.execute(
            "CREATE INDEX changes_byProjectOpen"
                + " ON "
                + table
                + " (open, dest_project_name, last_updated_on)");
      }
    }
  }
}
