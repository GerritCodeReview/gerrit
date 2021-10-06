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

import com.google.common.collect.Lists;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.config.ThreadSettingsConfig;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.Config;

public class Schema_127 extends SchemaVersion {
  private static final int SLICE_SIZE = 10000;
  private static final int MAX_BATCH_SIZE = 1000;
  // Use 10x threads for optimal performance
  private static final int THREADS = Runtime.getRuntime().availableProcessors() * 10;
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
    List<AccountPatchReview> accountPatchReviews = Lists.newArrayList();
    try (Statement s = newStatement(db);
        ResultSet rs = s.executeQuery("SELECT * from account_patch_reviews")) {
      while (rs.next()) {
        accountPatchReviews.add(
            new AccountPatchReview(
                rs.getInt("account_id"),
                rs.getInt("change_id"),
                rs.getInt("patch_set_id"),
                rs.getString("file_name")));
      }
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }

    ExecutorService pool = Executors.newFixedThreadPool(THREADS);
    try {
      for (List<AccountPatchReview> slice : Lists.partition(accountPatchReviews, SLICE_SIZE)) {
        @SuppressWarnings("unused")
        Future<?> unused =
            pool.submit(
                () -> {
                  try {
                    try (Connection con = jdbcAccountPatchReviewStore.getConnection();
                        PreparedStatement stmt =
                            con.prepareStatement(
                                "INSERT INTO account_patch_reviews "
                                    + "(account_id, change_id, patch_set_id, file_name) VALUES "
                                    + "(?, ?, ?, ?)")) {
                      int batchCount = 0;
                      for (AccountPatchReview accountPatchReview : slice) {
                        stmt.setInt(1, accountPatchReview.accountId);
                        stmt.setInt(2, accountPatchReview.changeId);
                        stmt.setInt(3, accountPatchReview.patchSetId);
                        stmt.setString(4, accountPatchReview.fileName);
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
                      throw jdbcAccountPatchReviewStore.convertError("insert", e);
                    }
                  } catch (OrmException e) {
                    throw new RuntimeException(e);
                  }
                });
      }
      pool.shutdown();
      pool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  public static final class AccountPatchReview {
    public final int accountId;
    public final int changeId;
    public final int patchSetId;
    public final String fileName;

    public AccountPatchReview(int accountId, int changeId, int patchSetId, String fileName) {
      this.accountId = accountId;
      this.changeId = changeId;
      this.patchSetId = patchSetId;
      this.fileName = fileName;
    }
  }
}
