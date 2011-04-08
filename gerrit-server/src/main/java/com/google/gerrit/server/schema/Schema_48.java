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

import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.ApprovalCategoryValue;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.sql.SQLException;
import java.sql.Statement;

import java.util.Collections;

public class Schema_48 extends SchemaVersion {
  @Inject
  Schema_48(Provider<Schema_47> prior) {
    super(prior);
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException, SQLException {
    // Read +3 allows merges to be uploaded
    db.approvalCategoryValues().insert(
        Collections.singleton(new ApprovalCategoryValue(
            new ApprovalCategoryValue.Id(ApprovalCategory.READ, (short) 3),
            "Upload merges permission")));
    // Since we added Read +3, elevate any Read +2 to that level to provide
    // access equivalent to prior schema versions.
    Statement stmt = ((JdbcSchema) db).getConnection().createStatement();
    try {
      stmt.execute("UPDATE ref_rights SET max_value = 3"
          + " WHERE category_id = '" + ApprovalCategory.READ.get()
          + "' AND max_value = 2");
    } finally {
      stmt.close();
    }
  }
}
