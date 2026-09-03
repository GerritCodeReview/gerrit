// Copyright (C) 2026 The Android Open Source Project
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

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.sql.SQLException;
import org.eclipse.jgit.lib.Config;

/**
 * H2 store that delegates locking to a plugin-provided {@link AccountPatchReviewDbLock}.
 *
 * <p>Activated by setting {@code accountPatchReviewDb.h2LockType = plugin}. A plugin must bind an
 * implementation via {@code DynamicItem.bind(binder(), AccountPatchReviewDbLock.class)}.
 */
@Singleton
public class H2PluginLockAccountPatchReviewStore extends H2CustomLockAccountPatchReviewStore {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private final DynamicItem<AccountPatchReviewDbLock> lockStrategy;

  @Inject
  H2PluginLockAccountPatchReviewStore(
      @GerritServerConfig Config cfg,
      SitePaths sitePaths,
      DynamicItem<AccountPatchReviewDbLock> lockStrategy) {
    super(cfg, sitePaths);
    this.lockStrategy = lockStrategy;
  }

  @Override
  public void start() {
    if (lockStrategy.get() == null) {
      throw new RuntimeException(
          "accountPatchReviewDb.h2LockType=plugin but no plugin has bound"
              + " AccountPatchReviewDbLock");
    }
    super.start();
    logger.atInfo().log(
        "AccountPatchReviewStore using H2 with plugin-provided locking (h2LockType=plugin)."
            + " lockTimeout=%d ms",
        getLockTimeoutMs());
  }

  @Nullable
  @Override
  protected Runnable tryAcquireLock() throws SQLException {
    return lockStrategy.get().tryAcquireLock();
  }
}
