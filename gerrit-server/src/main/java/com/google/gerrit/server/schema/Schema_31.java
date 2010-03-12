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
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.sql.SQLException;
import java.sql.Statement;

class Schema_31 extends SchemaVersion {
  @Inject
  Schema_31(Provider<Schema_30> prior) {
    super(prior);
  }

  @Override
  protected void migrateData(ReviewDb db) throws SQLException {
    Statement stmt = ((JdbcSchema) db).getConnection().createStatement();
    try {
      stmt.execute("CREATE INDEX changes_byProject"
          + " ON changes (dest_project_name)");
    } finally {
      stmt.close();
    }
  }
}
