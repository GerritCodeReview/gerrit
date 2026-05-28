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

import com.google.gerrit.server.config.SitePaths;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.eclipse.jgit.lib.Config;

/**
 * Abstract base for H2 stores that replace H2's built-in file locking with a custom mechanism.
 *
 * <p>H2 is opened with {@code FILE_LOCK=NO}; subclasses implement {@link #acquireExclusiveLock()}
 * to supply the actual lock.
 */
abstract class H2CustomLockAccountPatchReviewStore extends H2AccountPatchReviewStore {
  private final String url;

  protected H2CustomLockAccountPatchReviewStore(Config cfg, SitePaths sitePaths) {
    super();
    this.url =
        JdbcAccountPatchReviewStore.getUrl(cfg, sitePaths) + ";FILE_LOCK=NO;DB_CLOSE_DELAY=0";
  }

  /**
   * Acquires an exclusive lock and returns a {@link Runnable} that releases it.
   *
   * @throws SQLException if the lock cannot be acquired
   */
  protected abstract Runnable acquireExclusiveLock() throws SQLException;

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
              if ("close".equals(method.getName())) {
                try {
                  return method.invoke(con, args);
                } finally {
                  unlock.run();
                }
              }
              try {
                return method.invoke(con, args);
              } catch (InvocationTargetException e) {
                throw e.getCause();
              }
            });
  }
}
