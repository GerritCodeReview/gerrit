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

import com.google.gerrit.config.GerritServerConfig;
import com.google.gerrit.config.SitePaths;
import com.google.gerrit.config.ThreadSettingsConfig;
import com.google.gwtorm.server.OrmDuplicateKeyException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.sql.SQLException;
import java.sql.Statement;
import org.eclipse.jgit.lib.Config;

@Singleton
public class MysqlAccountPatchReviewStore extends JdbcAccountPatchReviewStore {

  @Inject
  MysqlAccountPatchReviewStore(
      @GerritServerConfig Config cfg,
      SitePaths sitePaths,
      ThreadSettingsConfig threadSettingsConfig) {
    super(cfg, sitePaths, threadSettingsConfig);
  }

  @Override
  public OrmException convertError(String op, SQLException err) {
    switch (getSQLStateInt(err)) {
      case 1022: // ER_DUP_KEY
      case 1062: // ER_DUP_ENTRY
      case 1169: // ER_DUP_UNIQUE;
        return new OrmDuplicateKeyException("ACCOUNT_PATCH_REVIEWS", err);

      default:
        if (err.getCause() == null && err.getNextException() != null) {
          err.initCause(err.getNextException());
        }
        return new OrmException(op + " failure on ACCOUNT_PATCH_REVIEWS", err);
    }
  }

  @Override
  protected void doCreateTable(Statement stmt) throws SQLException {
    stmt.executeUpdate(
        "CREATE TABLE IF NOT EXISTS account_patch_reviews ("
            + "account_id INTEGER DEFAULT 0 NOT NULL, "
            + "change_id INTEGER DEFAULT 0 NOT NULL, "
            + "patch_set_id INTEGER DEFAULT 0 NOT NULL, "
            + "file_name VARCHAR(255) DEFAULT '' NOT NULL, "
            + "CONSTRAINT primary_key_account_patch_reviews "
            + "PRIMARY KEY (change_id, patch_set_id, account_id, file_name)"
            + ")");
  }
}
