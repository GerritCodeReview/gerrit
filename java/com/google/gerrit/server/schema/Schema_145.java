// Copyright (C) 2017 The Android Open Source Project
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
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.StatementExecutor;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.sql.SQLException;

/** Create account_external_ids_byEmail index. */
public class Schema_145 extends SchemaVersion {

  @Inject
  Schema_145(Provider<Schema_144> prior) {
    super(prior);
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException, SQLException {
    JdbcSchema schema = (JdbcSchema) db;
    SqlDialect dialect = schema.getDialect();
    try (StatementExecutor e = newExecutor(db)) {
      try {
        dialect.dropIndex(e, "account_external_ids", "account_external_ids_byEmail");
      } catch (OrmException ex) {
        // Ignore.  The index did not exist.
      }
      e.execute(
          "CREATE INDEX account_external_ids_byEmail"
              + " ON account_external_ids"
              + " (email_address)");
    }
  }
}
