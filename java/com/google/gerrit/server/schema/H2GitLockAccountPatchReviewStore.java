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
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.internal.storage.file.LockFile;
import org.eclipse.jgit.lib.Config;

/**
 * H2 store using git-style {@link LockFile} locking for inter-process mutual exclusion.
 *
 * <p>Activated by setting {@code accountPatchReviewDb.h2LockType = git}. Each call to {@link
 * #getConnection()} atomically creates a {@code .lock} sidecar file before opening H2 and deletes
 * it when the connection is closed.
 */
@Singleton
public class H2GitLockAccountPatchReviewStore extends H2CustomLockAccountPatchReviewStore {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final long INITIAL_BACKOFF_MS = 1;
  private static final long MAX_BACKOFF_MS = 500;
  private static final long DEFAULT_LOCK_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(30);
  private final File lockTarget;
  private final long lockTimeoutMs;

  @Inject
  H2GitLockAccountPatchReviewStore(@GerritServerConfig Config cfg, SitePaths sitePaths) {
    super(cfg, sitePaths);
    String lockFilePath =
        cfg.getString(JdbcAccountPatchReviewStore.ACCOUNT_PATCH_REVIEW_DB, null, "h2LockFile");
    this.lockTarget =
        lockFilePath != null
            ? new File(lockFilePath)
            : sitePaths.db_dir.resolve("account_patch_reviews.lock").toFile();
    this.lockTimeoutMs =
        ConfigUtil.getTimeUnit(
            cfg,
            JdbcAccountPatchReviewStore.ACCOUNT_PATCH_REVIEW_DB,
            null,
            "h2LockTimeout",
            DEFAULT_LOCK_TIMEOUT_MS,
            TimeUnit.MILLISECONDS);
  }

  @Override
  public void start() {
    super.start();
    logger.atInfo().log(
        "AccountPatchReviewStore using H2 with git-style locking (h2LockType=git)."
            + " lockFile=%s lockTimeout=%d ms",
        lockTarget, lockTimeoutMs);
  }

  @Override
  protected Runnable acquireExclusiveLock() throws SQLException {
    LockFile lock = new LockFile(lockTarget);
    long backoffMs = INITIAL_BACKOFF_MS;
    long deadline = System.currentTimeMillis() + lockTimeoutMs;
    while (true) {
      try {
        if (lock.lock()) {
          return lock::unlock;
        }
      } catch (IOException e) {
        throw new SQLException("Failed to acquire git-style lock for H2 database", e);
      }
      long remaining = deadline - System.currentTimeMillis();
      if (remaining <= 0) {
        throw new SQLException("Could not acquire H2 lock within " + lockTimeoutMs + " ms");
      }
      long sleepMs = Math.min(backoffMs, remaining);
      logger.atFine().log("H2 git-style lock held by another process, retrying in %d ms", sleepMs);
      try {
        Thread.sleep(sleepMs);
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
        throw new SQLException("Interrupted while waiting for H2 lock");
      }
      backoffMs = Math.min(backoffMs * 2, MAX_BACKOFF_MS);
    }
  }
}
