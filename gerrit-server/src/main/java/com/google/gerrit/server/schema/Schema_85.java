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

import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gwtorm.jdbc.JdbcExecutor;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.gwtorm.schema.sql.SqlDialect;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.sql.SQLException;
import java.util.Set;

public class Schema_85 extends SchemaVersion {

  private final static String TABLE_NAME = "account_general_preferences";
  private final static String COLUMN1 = "change_screen";
  private final static String COLUMN2 = "reverse_patch_set_order";


  @Inject
  Schema_85(Provider<Schema_84> prior) {
    super(prior);
  }

  @Override
  protected void preUpdateSchema(ReviewDb db) throws OrmException, SQLException {
    JdbcSchema s = (JdbcSchema) db;
    SqlDialect dialect = ((JdbcSchema) db).getDialect();
    Set<String> existingColumns =
        dialect.listColumns(s.getConnection(), TABLE_NAME);
    if (!existingColumns.contains(COLUMN1)) {
      return;
    }
    dialect.dropColumn(new JdbcExecutor(s), TABLE_NAME, COLUMN1);
    if (!existingColumns.contains(COLUMN2)) {
      return;
    }
    dialect.dropColumn(new JdbcExecutor(s), TABLE_NAME, COLUMN2);
  }
}
