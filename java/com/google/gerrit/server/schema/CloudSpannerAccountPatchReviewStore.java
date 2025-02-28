// Copyright (C) 2023 The Android Open Source Project
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

import com.google.gerrit.exceptions.DuplicateKeyException;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.config.ThreadSettingsConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.sql.SQLException;
import java.sql.Statement;
import org.eclipse.jgit.lib.Config;

@Singleton
public class CloudSpannerAccountPatchReviewStore extends JdbcAccountPatchReviewStore {

  private static final int ERR_DUP_KEY = 6;

  @Inject
  CloudSpannerAccountPatchReviewStore(
      @GerritServerConfig Config cfg,
      SitePaths sitePaths,
      ThreadSettingsConfig threadSettingsConfig) {
    super(cfg, sitePaths, threadSettingsConfig);
  }

  @Override
  public StorageException convertError(String op, SQLException err) {
    return switch (err.getErrorCode()) {
      case ERR_DUP_KEY -> new DuplicateKeyException("ACCOUNT_PATCH_REVIEWS", err);
      default -> new StorageException(op + " failure on ACCOUNT_PATCH_REVIEWS", err);
    };
  }

  @Override
  protected void doCreateTable(Statement stmt) throws SQLException {
    stmt.executeUpdate(
        "CREATE TABLE IF NOT EXISTS account_patch_reviews ("
            + "account_id INT64 NOT NULL DEFAULT (0),"
            + "change_id INT64 NOT NULL DEFAULT (0),"
            + "patch_set_id INT64 NOT NULL DEFAULT (0),"
            + "file_name STRING(MAX) NOT NULL DEFAULT ('')"
            + ") PRIMARY KEY(change_id, patch_set_id, account_id, file_name)");
  }
}
