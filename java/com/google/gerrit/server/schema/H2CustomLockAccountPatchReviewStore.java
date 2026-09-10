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
import com.google.gerrit.server.config.SitePaths;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
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

  private final String url;
  private final long lockTimeoutMs;
  private final int lockBatchSize;
  private Lock lockInstance;

  protected H2CustomLockAccountPatchReviewStore(Config cfg, SitePaths sitePaths) {
    super();
    url = JdbcAccountPatchReviewStore.getUrl(cfg, sitePaths) + ";FILE_LOCK=NO;DB_CLOSE_DELAY=0";
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
        long deadline = System.currentTimeMillis() + unit.toMillis(time);
        Thread thread = Thread.currentThread();
        synchronized (this) {
          waiting.offer(thread);
          while (true) {
            if (acquired.contains(thread)) {
              return true;
            }
            if (acquired.isEmpty() && isTaskedToTryRaw(thread)) {
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
            long remainingMs = deadline - System.currentTimeMillis();
            if (remainingMs <= 0) {
              waiting.remove(thread);
              return false;
            }
            long waitMs = Math.min(remainingMs, backoffMs);
            logger.atFine().log("H2 lock held by another process, retrying in %d ms", waitMs);
            try {
              wait(waitMs);
            } catch (InterruptedException e) {
              waiting.remove(thread);
              // Could've been admitted.
              acquired.remove(thread);
              throw e;
            }
            backoffMs = Math.min(backoffMs * 2, MAX_BACKOFF_MS);
          }
        }
      }

      @Override
      public synchronized void unlock() {
        acquired.remove(Thread.currentThread());
        try {
          if (acquired.isEmpty()) {
            raw.unlock();
          }
        } finally {
          notifyAll();
        }
      }

      private boolean isTaskedToTryRaw(Thread thread) {
        // Only the head thread tries the raw lock. If it times out or is interrupted,
        // stopWaiting() removes it and the next thread becomes tasked to try.
        return waiting.peek() == thread;
      }

      private void admitBatch() {
        Iterator<Thread> it = waiting.iterator();
        int localAdmitted = 1;
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
