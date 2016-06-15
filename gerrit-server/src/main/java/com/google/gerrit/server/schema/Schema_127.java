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

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountPatchReview;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.change.H2AccountPatchReviewStore;
import com.google.gerrit.server.config.SitePaths;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class Schema_127 extends SchemaVersion {
  private static final int MAX_BATCH_SIZE = 1000;

  private final SitePaths sitePaths;

  @Inject
  Schema_127(Provider<Schema_126> prior, SitePaths sitePaths) {
    super(prior);
    this.sitePaths = sitePaths;
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException {
    String url = H2AccountPatchReviewStore.getUrl(sitePaths);
    H2AccountPatchReviewStore.dropTableIfExists(url);
    H2AccountPatchReviewStore.createTableIfNotExists(url);
    try (Connection con = DriverManager.getConnection(url);
        PreparedStatement stmt =
            con.prepareStatement("INSERT INTO ACCOUNT_PATCH_REVIEWS "
                + "(ACCOUNT_ID, CHANGE_ID, PATCH_SET_ID, FILE_NAME) VALUES "
                + "(?, ?, ?, ?)")) {
      int batchCount = 0;
      for (AccountPatchReview apr : db.accountPatchReviews()
          .iterateAllEntities()) {
        Account.Id accountId = apr.getKey().getParentKey();
        PatchSet.Id psId = apr.getKey().getPatchKey().getParentKey();
        String path = apr.getKey().getPatchKey().getFileName();
        stmt.setInt(1, accountId.get());
        stmt.setInt(2, psId.getParentKey().get());
        stmt.setInt(3, psId.get());
        stmt.setString(4, path);
        stmt.addBatch();
        batchCount++;
        if (batchCount >= MAX_BATCH_SIZE) {
          stmt.executeBatch();
          batchCount = 0;
        }
      }
      if (batchCount > 0) {
        stmt.executeBatch();
      }
    } catch (SQLException e) {
      throw H2AccountPatchReviewStore.convertError("insert", e);
    }
  }
}
