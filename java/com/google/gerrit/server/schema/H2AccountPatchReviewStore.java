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
import com.google.gwtorm.server.OrmDuplicateKeyException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.sql.SQLException;
import org.eclipse.jgit.lib.Config;

@Singleton
public class H2AccountPatchReviewStore extends JdbcAccountPatchReviewStore {

  @Inject
  H2AccountPatchReviewStore(
      @GerritServerConfig Config cfg,
      SitePaths sitePaths,
      ThreadSettingsConfig threadSettingsConfig) {
    super(cfg, sitePaths, threadSettingsConfig);
  }

  @Override
  public OrmException convertError(String op, SQLException err) {
    switch (getSQLStateInt(err)) {
      case 23001: // UNIQUE CONSTRAINT VIOLATION
      case 23505: // DUPLICATE_KEY_1
        return new OrmDuplicateKeyException("account_patch_reviews", err);

      default:
        if (err.getCause() == null && err.getNextException() != null) {
          err.initCause(err.getNextException());
        }
        return new OrmException(op + " failure on account_patch_reviews", err);
    }
  }
}
