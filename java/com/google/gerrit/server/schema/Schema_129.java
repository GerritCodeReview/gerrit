// Copyright (C) 2016 The Android Open Source Project
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
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.sql.SQLException;
import java.sql.Statement;

public class Schema_129 extends SchemaVersion {

  @Inject
  Schema_129(Provider<Schema_128> prior) {
    super(prior);
  }

  @Override
  protected void preUpdateSchema(ReviewDb db) throws OrmException {
    try (Statement stmt = ((JdbcSchema) db).getConnection().createStatement()) {
      stmt.execute("ALTER TABLE patch_sets MODIFY groups clob");
    } catch (SQLException e) {
      // Ignore.  Type may have already been modified manually.
    }
  }
}
