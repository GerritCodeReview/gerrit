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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.regex.Pattern;
import org.eclipse.jgit.internal.storage.file.LockFile;
import org.eclipse.jgit.lib.Config;

/**
 * H2 store using jgit-style {@link LockFile} locking for inter-process mutual exclusion.
 *
 * <p>Activated by setting {@code accountPatchReviewDb.h2LockType = jgit}. Each call to {@link
 * #getConnection()} atomically creates a {@code .lock} sidecar file before opening H2 and deletes
 * it when the connection is closed.
 */
@Singleton
public class H2JGitLockAccountPatchReviewStore extends H2CustomLockAccountPatchReviewStore {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final String H2_DB_URL_PREFIX = "jdbc:h2:file:";
  private static final long INITIAL_BACKOFF_MS = 1;
  private static final long MAX_BACKOFF_MS = 500;
  private final File lockTarget;

  @Inject
  H2JGitLockAccountPatchReviewStore(@GerritServerConfig Config cfg, SitePaths sitePaths) {
    super(cfg, sitePaths);
    this.lockTarget = lockTargetFromUrl(JdbcAccountPatchReviewStore.getUrl(cfg, sitePaths));
  }

  @VisibleForTesting
  static File lockTargetFromUrl(String h2Url) {
    if (!h2Url.startsWith(H2_DB_URL_PREFIX)) {
      throw new IllegalArgumentException("Not a valid H2 file URL: " + h2Url);
    }

    // URL format: "jdbc:h2:file:/path/to/db" - where ";" in the path is escaped as "\;"
    String path = h2Url.substring(H2_DB_URL_PREFIX.length());

    // Split on first unescaped ";" to drop options, then unescape "\;" in the path
    return new File(
        Iterables.get(Splitter.on(Pattern.compile("(?<!\\\\);")).split(path), 0)
            .replace("\\;", ";"));
  }

  @Override
  public void start() {
    super.start();
    logger.atInfo().log(
        "AccountPatchReviewStore using H2 with jgit-style locking (h2LockType=jgit)."
            + " lockFile=%s lockTimeout=%d ms",
        lockTarget, getLockTimeoutMs());
  }

  /**
   * Creates a {@link Lock} whose {@link Lock#tryLock(long, TimeUnit)} creates and acquires a
   * jgit-style {@link LockFile}, retrying with backoff until the given wait time elapses.
   */
  @Override
  protected Lock newLock() {
    return new Lock() {
      private LockFile lockFile = new LockFile(lockTarget);

      @Override
      public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        long backoffMs = INITIAL_BACKOFF_MS;
        long deadline = System.currentTimeMillis() + unit.toMillis(time);
        while (!tryAcquireOnce()) {
          long remaining = deadline - System.currentTimeMillis();
          if (remaining <= 0) {
            return false;
          }
          long sleepMs = Math.min(backoffMs, remaining);
          logger.atFine().log("H2 lock held by another process, retrying in %d ms", sleepMs);
          Thread.sleep(sleepMs);
          backoffMs = Math.min(backoffMs * 2, MAX_BACKOFF_MS);
        }
        return true;
      }

      private boolean tryAcquireOnce() {
        try {
          return lockFile.lock();
        } catch (IOException e) {
          logger.atInfo().withCause(e).log("Failed to acquire jgit-style lock for H2 database");
          return false;
        }
      }

      @Override
      public void unlock() {
        lockFile.unlock();
      }

      @Override
      public void lock() {
        throw new UnsupportedOperationException();
      }

      @Override
      public void lockInterruptibly() {
        throw new UnsupportedOperationException();
      }

      @Override
      public boolean tryLock() {
        throw new UnsupportedOperationException();
      }

      @Override
      public Condition newCondition() {
        throw new UnsupportedOperationException();
      }
    };
  }
}
