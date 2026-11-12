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

package com.google.gerrit.server.git.receive;

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.BranchNameKey;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Locks that serialize the pushes for review that look up the same change on the same branch.
 *
 * <p>A push looks up the existing change of each commit by its Change-Id (or by the commit, if it
 * has no Change-Id) in the change index, and creates a new change when it finds none. The index is
 * updated only after the change is created in the repository, so a push that looks up the change in
 * between doesn't find it, and creates a duplicate change with the same Change-Id on the same
 * branch.
 *
 * <p>A push holds the lock of each lookup until the changes that it created are in the index, so
 * that a push of the same change waits, and then finds them.
 *
 * <p>There is a lock per project, branch and key, so pushes of different changes never wait for
 * each other. A lock is removed when no push holds it or waits for it.
 *
 * <p>The locks are local to this server. They don't serialize pushes to different servers of a
 * cluster.
 */
@Singleton
public class ChangeLookupLocks {
  /** A held lock. */
  final class Handle {
    private final String name;
    private final Entry entry;
    private boolean released;

    private Handle(String name, Entry entry) {
      this.name = name;
      this.entry = entry;
    }

    /** Releases the lock. Can be called from any thread, and more than once. */
    void release() {
      if (released) {
        return;
      }
      released = true;
      entry.semaphore.release();
      unref(name, entry);
    }
  }

  private static final class Entry {
    // A semaphore instead of a lock, since it can be released by a thread other than the one that
    // acquired it.
    final Semaphore semaphore = new Semaphore(1, /* fair= */ true);

    /** The pushes that hold or wait for the lock. Guarded by the {@link ChangeLookupLocks}. */
    int users;
  }

  private final Map<String, Entry> entries = new HashMap<>();

  /**
   * Acquires the lock of a lookup on a branch.
   *
   * <p>The lock isn't reentrant, the caller must not acquire a lock that it holds.
   *
   * @param dest the branch.
   * @param key the Change-Id, or the commit SHA-1 for a commit without a Change-Id.
   * @param timeout how long to wait for the lock.
   * @param unit the unit of {@code timeout}.
   * @return the held lock, or {@code null} if it wasn't acquired within the timeout.
   */
  @Nullable
  Handle tryLock(BranchNameKey dest, String key, long timeout, TimeUnit unit)
      throws InterruptedException {
    String name = dest.project().get() + '\0' + dest.branch() + '\0' + key;
    Entry entry;
    synchronized (this) {
      entry = entries.computeIfAbsent(name, n -> new Entry());
      entry.users++;
    }
    boolean acquired = false;
    try {
      acquired = entry.semaphore.tryAcquire(timeout, unit);
    } finally {
      if (!acquired) {
        unref(name, entry);
      }
    }
    return acquired ? new Handle(name, entry) : null;
  }

  private synchronized void unref(String name, Entry entry) {
    if (--entry.users == 0) {
      entries.remove(name);
    }
  }

  @VisibleForTesting
  synchronized int size() {
    return entries.size();
  }
}
