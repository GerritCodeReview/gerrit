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

import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.SitePaths;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import org.eclipse.jgit.lib.Config;

/**
 * Abstract base for H2 stores that replace H2's built-in file locking with a custom mechanism.
 *
 * <p>H2 is opened with {@code FILE_LOCK=NO}; subclasses implement {@link #newLock()}, returning a
 * {@link Lock} whose {@link Lock#tryLock(long, TimeUnit)} implementation is responsible for its own
 * retry and backoff strategy, up to the given wait time.
 */
abstract class H2CustomLockAccountPatchReviewStore extends H2AccountPatchReviewStore {
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

  /** Creates a new, not-yet-acquired {@link Lock}. */
  protected abstract Lock newLock();

  @Override
  public Connection getConnection() throws SQLException {
    Lock lock = newLock();

    try {
      if (!lock.tryLock(lockTimeoutMs, TimeUnit.MILLISECONDS)) {
        throw new SQLException("Could not acquire H2 lock within " + lockTimeoutMs + " ms");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new SQLException("Interrupted while waiting for H2 lock", e);
    }

    try {
      return lockingConnection(DriverManager.getConnection(url), lock);
    } catch (SQLException e) {
      lock.unlock();
      throw e;
    }
  }

  private static Connection lockingConnection(Connection con, Lock lock) {
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
                  lock.unlock();
                }
              }
            });
  }
}
