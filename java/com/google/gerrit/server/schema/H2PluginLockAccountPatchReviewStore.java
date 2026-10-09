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
import com.google.gerrit.extensions.registration.Extension;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.plugins.Plugin;
import com.google.gerrit.server.plugins.StopPluginListener;
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
 *
 * <p>The lock manager is resolved at the start of every batch, so a plugin reload takes effect at
 * the next batch. While no plugin-provided lock manager is bound, locking fails closed.
 *
 * <p>Before the plugin that provides the {@link LockManager} is stopped or reloaded, {@link
 * #beforeStopPlugin} stops new batches from using its lock manager and waits for the in-flight
 * batch to release its lock. {@code PluginLoader} only rebinds the {@code DynamicItem} after this
 * returns.
 */
@Singleton
public class H2PluginLockAccountPatchReviewStore extends H2CustomLockAccountPatchReviewStore
    implements StopPluginListener {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private final DynamicItem<LockManager> lockManager;
  private volatile Extension<LockManager> stopping;

  @Inject
  H2PluginLockAccountPatchReviewStore(
      @GerritServerConfig Config cfg, SitePaths sitePaths, DynamicItem<LockManager> lockManager) {
    super(cfg, sitePaths);
    this.lockManager = lockManager;
  }

  @Override
  public void start() {
    if (lockManager.get() instanceof DefaultLockManager) {
      throw new IllegalStateException(
          "accountPatchReviewDb.h2LockType=plugin requires a LockManager implementation provided"
              + " by a plugin, but only the default in-memory LockManager is bound; install a"
              + " plugin that provides LockManager, or use a different h2LockType");
    }
    super.start();
    logger.atInfo().log(
        "AccountPatchReviewStore using H2 with LockManager-provided locking (h2LockType=plugin)."
            + " lockTimeout=%d ms",
        getLockTimeoutMs());
  }

  @Override
  public void beforeStopPlugin(Plugin plugin) {
    Extension<LockManager> entry = lockManager.getEntry();
    if (entry == null || !plugin.getName().equals(entry.getPluginName())) {
      return;
    }
    stopping = entry;
    try {
      awaitIdle();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      logger.atSevere().withCause(e).log(
          "Interrupted while waiting for AccountPatchReviewStore lock of plugin %s",
          plugin.getName());
    }
  }

  @Override
  public void onStopPlugin(Plugin plugin) {
    Extension<LockManager> entry = stopping;
    if (entry != null && plugin.getName().equals(entry.getPluginName())) {
      stopping = null;
    }
  }

  @Nullable
  @Override
  protected Lock newLock() {
    Extension<LockManager> entry = lockManager.getEntry();
    if (entry.equals(stopping)) {
      return null;
    }
    LockManager current = entry.get();
    if (current instanceof DefaultLockManager) {
      throw new IllegalStateException(
          "LockManager provided by plugin is no longer bound (plugin unloaded?); refusing to fall"
              + " back to the default in-memory LockManager");
    }
    return current.getLock(CoreLockKeys.ACCOUNT_PATCH_REVIEW_DB);
  }

  @Override
  protected String lockType() {
    return "plugin";
  }
}
