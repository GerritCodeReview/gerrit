// Copyright (C) 2011 The Android Open Source Project
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
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.sql.SQLException;
import java.sql.Statement;

public class Schema_57 extends SchemaVersion {

  @Inject
  Schema_57(Provider<Schema_56> prior) {
    super(prior);
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException,
      SQLException {
    Statement stmt = ((JdbcSchema) db).getConnection().createStatement();
    try {
      stmt.execute("ALTER TABLE accounts " +
      		       "ADD email_on_first_patch_set_of_change " +
      		       "CHAR(1) DEFAULT 'Y' NOT NULL");
    } finally {
      stmt.close();
    }
  }
}
