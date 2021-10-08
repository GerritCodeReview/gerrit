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
import org.eclipse.jgit.lib.Config;

public class Schema_127 extends SchemaVersion {
  // we can use multiple threads per CPU as we can expect that threads will be
  // waiting for IO
  private static final int THREADS_PER_CPU = 10;
  private static final int SLICE_SIZE = 10000;
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

    ExecutorService pool = Executors.newFixedThreadPool(getThreadCount());
    for (List<AccountPatchReview> slice : Lists.partition(accountPatchReviews, SLICE_SIZE)) {
      pool.execute(
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
  }

  private static final class AccountPatchReview {
    private final int accountId;
    private final int changeId;
    private final int patchSetId;
    private final String fileName;

    public AccountPatchReview(int accountId, int changeId, int patchSetId, String fileName) {
      this.accountId = accountId;
      this.changeId = changeId;
      this.patchSetId = patchSetId;
      this.fileName = fileName;
    }
  }

  private int getThreadCount() {
    int threads;
    try {
      threads = Integer.parseInt(System.getProperty("schema127_threadcount"));
    } catch (NumberFormatException e) {
      threads = Runtime.getRuntime().availableProcessors() * THREADS_PER_CPU;
    }
    return threads;
  }
}
