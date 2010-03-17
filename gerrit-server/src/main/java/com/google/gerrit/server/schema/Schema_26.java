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
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.gwtorm.schema.sql.DialectMySQL;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.sql.SQLException;
import java.sql.Statement;

class Schema_26 extends SchemaVersion {
  @Inject
  Schema_26(Provider<Schema_25> prior) {
    super(prior);
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws SQLException {
    if (((JdbcSchema) db).getDialect() instanceof DialectMySQL) {
      Statement stmt = ((JdbcSchema) db).getConnection().createStatement();
      try {
        stmt.execute("ALTER TABLE account_group_members_audit" //
            + " MODIFY removed_on TIMESTAMP NULL DEFAULT NULL");
        stmt.execute("UPDATE account_group_members_audit" //
            + " SET removed_on = NULL" //
            + " WHERE removed_by IS NULL;");
      } finally {
        stmt.close();
      }
    }
  }
}
