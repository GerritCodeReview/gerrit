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

import static com.google.common.base.Equivalence.identity;

import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.SitePaths;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.regex.Pattern;
import org.eclipse.jgit.lib.Config;

/**
 * Abstract base for H2 stores that replace H2's built-in file locking with a custom mechanism.
 *
 * <p>H2 is opened with {@code FILE_LOCK=NO}; subclasses implement {@link #newLock()}, returning a
 * raw {@link Lock} with a working {@link Lock#tryLock()} and {@link Lock#unlock()}. This class adds
 * retry-with-backoff (up to {@code h2LockTimeout}) and batching: up to {@code h2LockBatchSize}
 * in-process callers can share one held lock at a time instead of each acquiring/releasing
 * separately.
 */
abstract class H2CustomLockAccountPatchReviewStore extends H2AccountPatchReviewStore {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final long DEFAULT_LOCK_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(30);
  private static final int DEFAULT_LOCK_BATCH_SIZE = 32;
  private static final long INITIAL_BACKOFF_MS = 1;
  private static final long MAX_BACKOFF_MS = 500;
  private static final String H2_DB_URL_PREFIX = "jdbc:h2:file:";
  private static final String LOCK_TYPE_MARKER_SUFFIX = ".locktype";

  private final String url;
  private final File dbFile;
  private final long lockTimeoutMs;
  private final int lockBatchSize;
  private Lock lockInstance;

  protected H2CustomLockAccountPatchReviewStore(Config cfg, SitePaths sitePaths) {
    super();
    String baseUrl = JdbcAccountPatchReviewStore.getUrl(cfg, sitePaths);
    dbFile = dbFileFromUrl(baseUrl);
    url = baseUrl + ";FILE_LOCK=NO;DB_CLOSE_DELAY=0";
    lockTimeoutMs =
        ConfigUtil.getTimeUnit(
            cfg,
            JdbcAccountPatchReviewStore.ACCOUNT_PATCH_REVIEW_DB,
            null,
            "h2LockTimeout",
            DEFAULT_LOCK_TIMEOUT_MS,
            TimeUnit.MILLISECONDS);
    lockBatchSize =
        Math.max(
            1,
            cfg.getInt(
                JdbcAccountPatchReviewStore.ACCOUNT_PATCH_REVIEW_DB,
                "h2LockBatchSize",
                DEFAULT_LOCK_BATCH_SIZE));
  }

  protected long getLockTimeoutMs() {
    return lockTimeoutMs;
  }

  protected int getLockBatchSize() {
    return lockBatchSize;
  }

  /** Name of this locking mechanism, recorded next to the DB to detect mixed configurations. */
  protected abstract String lockType();

  @Override
  public void start() {
    checkLockTypeMarker(dbFile, lockType());
    super.start();
  }

  static File dbFileFromUrl(String h2Url) {
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

  /**
   * Records the lock type next to the DB on first use and fails if the DB was already claimed by a
   * different lock type, since primaries using different locking would not exclude each other.
   *
   * <p>The marker is published by hard-linking a fully written temp file, which is atomic and fails
   * if the marker already exists, so a marker is never visible without its content.
   */
  static void checkLockTypeMarker(File dbFile, String lockType) {
    Path marker =
        dbFile
            .getAbsoluteFile()
            .toPath()
            .resolveSibling(dbFile.getName() + LOCK_TYPE_MARKER_SUFFIX);
    Path tmp = null;
    try {
      tmp = Files.createTempFile(marker.getParent(), dbFile.getName(), ".locktype.tmp");
      Files.writeString(tmp, lockType);
      try {
        Files.createLink(marker, tmp);
        return;
      } catch (FileAlreadyExistsException e) {
        // Another primary already claimed the DB; compare below.
      }
      String existing = Files.readString(marker).trim();
      if (!existing.equals(lockType)) {
        throw new IllegalStateException(
            String.format(
                "H2 account patch review DB %s is used with h2LockType=%s by other primaries"
                    + " (see %s), but this server is configured with h2LockType=%s. All primaries"
                    + " must use the same locking. To change it, stop all primaries and delete"
                    + " the marker file.",
                dbFile, existing, marker, lockType));
      }
    } catch (IOException e) {
      throw new IllegalStateException("Cannot read or write lock type marker " + marker, e);
    } finally {
      if (tmp != null) {
        try {
          Files.deleteIfExists(tmp);
        } catch (IOException e) {
          logger.atWarning().withCause(e).log("Cannot delete temp file %s", tmp);
        }
      }
    }
  }

  /** Creates a new, not-yet-acquired raw {@link Lock}; only tryLock()/unlock() are used. */
  protected abstract Lock newLock();

  private synchronized Lock lock() {
    if (lockInstance == null) {
      lockInstance = newBatchingLock(newLock(), lockBatchSize);
    }
    return lockInstance;
  }

  /** Wraps {@code raw} with retry-with-backoff and fixed-size batching. */
  static Lock newBatchingLock(Lock raw, int lockBatchSize) {
    return new Lock() {
      // Threads not yet admitted, in the order they arrived.
      private final Queue<Thread> waiting = new ArrayDeque<>();
      // Threads currently holding the lock.
      private final Set<Thread> acquired = new HashSet<>();
      private final int maxActive = Math.max(1, lockBatchSize);

      @Override
      public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        long backoffMs = INITIAL_BACKOFF_MS;
        long deadline = System.nanoTime() + unit.toNanos(time);
        Thread thread = Thread.currentThread();
        synchronized (this) {
          waiting.offer(thread);
          while (true) {
            if (acquired.contains(thread)) {
              return true;
            }
            if (acquired.isEmpty() && isTaskedToTryRaw(thread)) {
              if (tryAcquireRaw()) {
                return true;
              }
            }
            try {
              long remainingNanos = deadline - System.nanoTime();
              if (remainingNanos <= 0) {
                waiting.remove(thread);
                return false;
              }
              long waitMs = Math.clamp(TimeUnit.NANOSECONDS.toMillis(remainingNanos), 1, backoffMs);
              logger.atFine().log("H2 lock held by another process, retrying in %d ms", waitMs);
              wait(waitMs);
              backoffMs = Math.min(backoffMs * 2, MAX_BACKOFF_MS);
            } catch (InterruptedException | RuntimeException e) {
              unlock(thread);
              throw e;
            }
          }
        }
      }

      @Override
      public synchronized void unlock() {
        unlock(Thread.currentThread());
      }

      private void unlock(Thread thread) {
        acquired.remove(thread);
        try {
          if (acquired.isEmpty()) {
            raw.unlock();
          }
        } finally {
          notifyAll();
        }
      }

      private boolean tryAcquireRaw() {
        boolean rawAcquired = false;
        try {
          rawAcquired = raw.tryLock();
        } catch (RuntimeException e) {
          logger.atSevere().withCause(e).log(
              "Exception while trying to lock for AccountPatchReviewStore");
        }
        try {
          if (rawAcquired) {
            admitBatch();
          }
        } catch (RuntimeException e) {
          logger.atSevere().withCause(e).log(
              "Exception while allowing next batch of waiting threads");
        }
        return rawAcquired;
      }

      private boolean isTaskedToTryRaw(Thread thread) {
        // Only the head thread tries the raw lock. If it times out or is interrupted,
        // stopWaiting() removes it and the next thread becomes tasked to try.
        return identity().equivalent(waiting.peek(), thread);
      }

      private void admitBatch() {
        Iterator<Thread> it = waiting.iterator();
        int localAdmitted = 0;
        while (it.hasNext() && localAdmitted++ < maxActive) {
          acquired.add(it.next());
          it.remove();
        }
        // This wakes up all the threads waiting in the queue so that they loop
        // to check their admitted status.
        notifyAll();
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

  @Override
  public Connection getConnection() throws SQLException {
    Lock lock = lock();
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
