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
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.SitePaths;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.Config;

/**
 * Abstract base for H2 stores that replace H2's built-in file locking with a custom mechanism.
 *
 * <p>H2 is opened with {@code FILE_LOCK=NO}; subclasses implement {@link #tryAcquireLock()} to make
 * a single lock attempt. The retry loop with exponential backoff is handled here.
 */
abstract class H2CustomLockAccountPatchReviewStore extends H2AccountPatchReviewStore {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final long INITIAL_BACKOFF_MS = 1;
  private static final long MAX_BACKOFF_MS = 500;
  static final long DEFAULT_LOCK_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(30);

  private final String url;
  private final long lockTimeoutMs;

  protected H2CustomLockAccountPatchReviewStore(Config cfg, SitePaths sitePaths) {
    super();
    this.url =
        JdbcAccountPatchReviewStore.getUrl(cfg, sitePaths) + ";FILE_LOCK=NO;DB_CLOSE_DELAY=0";
    this.lockTimeoutMs =
        ConfigUtil.getTimeUnit(
            cfg,
            JdbcAccountPatchReviewStore.ACCOUNT_PATCH_REVIEW_DB,
            null,
            "h2LockTimeout",
            DEFAULT_LOCK_TIMEOUT_MS,
            TimeUnit.MILLISECONDS);
  }

  protected long getLockTimeoutMs() {
    return lockTimeoutMs;
  }

  /**
   * Makes a single attempt to acquire the lock.
   *
   * @return a {@link Runnable} that releases the lock, or {@code null} if the lock is currently
   *     held by another process (retry-able)
   * @throws SQLException on a hard, non-retryable error
   */
  @Nullable
  protected abstract Runnable tryAcquireLock() throws SQLException;

  private Runnable acquireExclusiveLock() throws SQLException {
    long backoffMs = INITIAL_BACKOFF_MS;
    long deadline = System.currentTimeMillis() + lockTimeoutMs;
    while (true) {
      Runnable unlock = tryAcquireLock();
      if (unlock != null) {
        return unlock;
      }
      long remaining = deadline - System.currentTimeMillis();
      if (remaining <= 0) {
        throw new SQLException("Could not acquire H2 lock within " + lockTimeoutMs + " ms");
      }
      long sleepMs = Math.min(backoffMs, remaining);
      logger.atFine().log("H2 lock held by another process, retrying in %d ms", sleepMs);
      try {
        Thread.sleep(sleepMs);
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
        throw new SQLException("Interrupted while waiting for H2 lock", ie);
      }
      backoffMs = Math.min(backoffMs * 2, MAX_BACKOFF_MS);
    }
  }

  @Override
  public Connection getConnection() throws SQLException {
    Runnable unlock = acquireExclusiveLock();
    try {
      return lockingConnection(DriverManager.getConnection(url), unlock);
    } catch (SQLException e) {
      unlock.run();
      throw e;
    }
  }

  private static Connection lockingConnection(Connection con, Runnable unlock) {
    return (Connection)
        Proxy.newProxyInstance(
            Connection.class.getClassLoader(),
            new Class<?>[] {Connection.class},
            (proxy, method, args) -> {
              try {
                return method.invoke(con, args);
              } catch (InvocationTargetException e) {
                throw e.getCause();
              } finally {
                if ("close".equals(method.getName())) {
                  unlock.run();
                }
              }
            });
  }
}
