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
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.plugincontext.PluginItemContext;
import com.google.gerrit.server.project.CoreLockKeys;
import com.google.gerrit.server.project.DefaultLockManager;
import com.google.gerrit.server.project.LockManager;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.concurrent.locks.Lock;
import org.eclipse.jgit.lib.Config;

/**
 * H2 store that delegates locking to the site's {@link LockManager}.
 *
 * <p>Activated by setting {@code accountPatchReviewDb.h2LockType = plugin}. Requires a plugin to
 * override the default {@link DefaultLockManager} binding for {@link LockManager} (e.g. via {@code
 * DynamicItem<LockManager>.set(...)}) to provide distributed locking; {@link #start()} throws if
 * only the default in-memory lock manager is bound.
 */
@Singleton
public class H2PluginLockAccountPatchReviewStore extends H2CustomLockAccountPatchReviewStore {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private final PluginItemContext<LockManager> lockManager;

  @Inject
  H2PluginLockAccountPatchReviewStore(
      @GerritServerConfig Config cfg,
      SitePaths sitePaths,
      PluginItemContext<LockManager> lockManager) {
    super(cfg, sitePaths);
    this.lockManager = lockManager;
  }

  @Override
  public void start() {
    if (!lockManager.hasImplementation()) {
      throw new RuntimeException(
          "accountPatchReviewDb.h2LockType=plugin requires a LockManager binding");
    }
    Void unused =
        lockManager.call(
            lm -> {
              if (lm instanceof DefaultLockManager) {
                throw new RuntimeException(
                    "accountPatchReviewDb.h2LockType=plugin requires a LockManager implementation"
                        + " provided by a plugin, but only the default in-memory LockManager is"
                        + " bound; install a plugin that provides LockManager, or use a different"
                        + " h2LockType");
              }
              return null;
            });
    super.start();
    logger.atInfo().log(
        "AccountPatchReviewStore using H2 with LockManager-provided locking (h2LockType=plugin)."
            + " lockTimeout=%d ms",
        getLockTimeoutMs());
  }

  @Override
  protected Lock newLock() {
    return lockManager.call(lm -> lm.getLock(CoreLockKeys.ACCOUNT_PATCH_REVIEW_DB));
  }
}
