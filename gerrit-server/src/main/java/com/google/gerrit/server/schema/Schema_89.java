// Copyright (C) 2013 The Android Open Source Project
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

import com.google.common.collect.ImmutableList;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.gwtorm.schema.sql.DialectMySQL;
import com.google.gwtorm.schema.sql.SqlDialect;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.sql.SQLException;
import java.sql.Statement;

public class Schema_89 extends SchemaVersion {
  @Inject
  Schema_89(Provider<Schema_88> prior) {
    super(prior);
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException,
      SQLException {
    SqlDialect dialect = ((JdbcSchema) db).getDialect();
    Statement stmt = ((JdbcSchema) db).getConnection().createStatement();
    try {
      for (String name : ImmutableList.of(
          "patch_set_approvals_openByUser",
          "patch_set_approvals_closedByU")) {
        if (dialect instanceof DialectMySQL) {
          stmt.executeUpdate("DROP INDEX " + name + " ON patch_set_approvals");
        } else {
          stmt.executeUpdate("DROP INDEX " + name);
        }
      }
    } finally {
      stmt.close();
    }
  }
}
