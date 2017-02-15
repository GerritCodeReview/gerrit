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

import com.google.common.base.Strings;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.HashedPassword;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class Schema_142 extends SchemaVersion {
  private static final int MAX_BATCH_SIZE = 1000;

  @Inject
  Schema_142(Provider<Schema_141> prior) {
    super(prior);
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException, SQLException {
    try (PreparedStatement updateStmt =
        ((JdbcSchema) db)
            .getConnection()
            .prepareStatement(
                "UPDATE account_external_ids "
                    + "SET hashed_password = ? "
                    + "WHERE external_id = ?")) {
      int batchCount = 0;

      try (Statement stmt = newStatement(db);
          ResultSet rs =
              stmt.executeQuery("SELECT external_id, password FROM account_external_ids")) {
        while (rs.next()) {
          String password = rs.getString("password");
          if (Strings.isNullOrEmpty(password)) {
            continue;
          }

          HashedPassword hashed = HashedPassword.fromPassword(password);
          updateStmt.setString(1, hashed.encode());
          updateStmt.setString(2, rs.getString("external_id"));
          updateStmt.addBatch();
          batchCount++;
          if (batchCount >= MAX_BATCH_SIZE) {
            updateStmt.executeBatch();
            batchCount = 0;
          }
        }
      }

      if (batchCount > 0) {
        updateStmt.executeBatch();
      }
    }
  }
}
