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

import com.google.gerrit.config.GerritServerConfig;
import com.google.gerrit.config.SitePaths;
import com.google.gerrit.config.ThreadSettingsConfig;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.eclipse.jgit.lib.Config;

public class Schema_127 extends SchemaVersion {
  private static final int MAX_BATCH_SIZE = 1000;

  private final SitePaths sitePaths;
  private final Config cfg;
  private final ThreadSettingsConfig threadSettingsConfig;

  @Inject
  Schema_127(
      Provider<Schema_126> prior,
      SitePaths sitePaths,
      @GerritServerConfig Config cfg,
      ThreadSettingsConfig threadSettingsConfig) {
    super(prior);
    this.sitePaths = sitePaths;
    this.cfg = cfg;
    this.threadSettingsConfig = threadSettingsConfig;
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException {
    JdbcAccountPatchReviewStore jdbcAccountPatchReviewStore =
        JdbcAccountPatchReviewStore.createAccountPatchReviewStore(
            cfg, sitePaths, threadSettingsConfig);
    jdbcAccountPatchReviewStore.dropTableIfExists();
    jdbcAccountPatchReviewStore.createTableIfNotExists();
    try (Connection con = jdbcAccountPatchReviewStore.getConnection();
        PreparedStatement stmt =
            con.prepareStatement(
                "INSERT INTO account_patch_reviews "
                    + "(account_id, change_id, patch_set_id, file_name) VALUES "
                    + "(?, ?, ?, ?)")) {
      int batchCount = 0;

      try (Statement s = newStatement(db);
          ResultSet rs = s.executeQuery("SELECT * from account_patch_reviews")) {
        while (rs.next()) {
          stmt.setInt(1, rs.getInt("account_id"));
          stmt.setInt(2, rs.getInt("change_id"));
          stmt.setInt(3, rs.getInt("patch_set_id"));
          stmt.setString(4, rs.getString("file_name"));
          stmt.addBatch();
          batchCount++;
          if (batchCount >= MAX_BATCH_SIZE) {
            stmt.executeBatch();
            batchCount = 0;
          }
        }
      }
      if (batchCount > 0) {
        stmt.executeBatch();
      }
    } catch (SQLException e) {
      throw jdbcAccountPatchReviewStore.convertError("insert", e);
    }
  }
}
