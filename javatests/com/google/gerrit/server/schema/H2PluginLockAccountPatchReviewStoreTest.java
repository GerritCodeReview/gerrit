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

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.plugins.Plugin;
import com.google.gerrit.server.project.CoreLockKeys;
import com.google.gerrit.server.project.DefaultLockManager;
import com.google.gerrit.server.project.LockKey;
import com.google.gerrit.server.project.LockManager;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.function.Supplier;
import org.eclipse.jgit.lib.Config;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

public class H2PluginLockAccountPatchReviewStoreTest {
  private static final Account.Id ACCOUNT = Account.id(1);
  private static final PatchSet.Id PS = PatchSet.id(Change.id(1), 1);

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private SitePaths sitePaths;
  private Config cfg;
  private DynamicItem<LockManager> lockManagerItem;

  @Before
  public void setUp() throws Exception {
    sitePaths = new SitePaths(temporaryFolder.getRoot().toPath());
    Files.createDirectories(sitePaths.db_dir);
    cfg = new Config();
    cfg.setString("accountPatchReviewDb", null, "h2LockTimeout", "300 ms");
    lockManagerItem = DynamicItem.itemOf(LockManager.class, new DefaultLockManager());
  }

  @Test
  public void start_failsWhenOnlyDefaultLockManagerIsBound() {
    H2PluginLockAccountPatchReviewStore store = newStore();
    assertThrows(IllegalStateException.class, store::start);
  }

  @Test
  public void usesAccountPatchReviewDbLockKey() {
    FakeLockManager manager = new FakeLockManager();
    var unused = lockManagerItem.set(manager, "plugin");
    H2PluginLockAccountPatchReviewStore store = newStore();
    store.start();
    int locksAfterStart = manager.lock.locks.get();

    assertThat(store.markReviewed(PS, ACCOUNT, "foo.txt")).isTrue();

    assertThat(ImmutableSet.copyOf(manager.requestedKeys))
        .containsExactly(CoreLockKeys.ACCOUNT_PATCH_REVIEW_DB);
    assertThat(manager.lock.locks.get()).isEqualTo(locksAfterStart + 1);
    assertThat(manager.lock.unlocks.get()).isEqualTo(manager.lock.locks.get());
  }

  @Test
  public void resolvesLockManagerForEachBatch() throws Exception {
    FakeLockManager first = new FakeLockManager();
    RegistrationHandle firstHandle = lockManagerItem.set(first, "plugin-1");
    H2PluginLockAccountPatchReviewStore store = newStore();
    store.start();
    int firstLocksAfterStart = first.lock.locks.get();

    assertThat(store.markReviewed(PS, ACCOUNT, "a.txt")).isTrue();
    firstHandle.remove();
    FakeLockManager second = new FakeLockManager();
    var unused = lockManagerItem.set(second, "plugin-2");
    assertThat(store.markReviewed(PS, ACCOUNT, "b.txt")).isTrue();

    assertThat(first.lock.locks.get()).isEqualTo(firstLocksAfterStart + 1);
    assertThat(first.lock.unlocks.get()).isEqualTo(first.lock.locks.get());
    assertThat(second.lock.locks.get()).isEqualTo(1);
    assertThat(second.lock.unlocks.get()).isEqualTo(1);
  }

  @Test
  public void failsClosedWhenPluginLockManagerIsUnloaded() {
    RegistrationHandle handle = lockManagerItem.set(new FakeLockManager(), "plugin");
    H2PluginLockAccountPatchReviewStore store = newStore();
    store.start();

    handle.remove();

    assertThrows(SQLException.class, store::getConnection);
  }

  @Test
  public void batch_acquiresAndReleasesRawLockOncePerBatch() throws Exception {
    TestLock raw = new TestLock();
    Lock batching = H2CustomLockAccountPatchReviewStore.newBatchingLock(() -> raw, 2);
    CountDownLatch bothHeld = new CountDownLatch(2);
    List<Throwable> errors = new CopyOnWriteArrayList<>();

    // Keep the raw lock busy so that both threads queue up and are admitted as a single batch.
    assertThat(raw.tryLock()).isTrue();
    List<Thread> threads = new ArrayList<>();
    for (int i = 0; i < 2; i++) {
      Thread t =
          new Thread(
              () -> {
                try {
                  assertThat(batching.tryLock(10, TimeUnit.SECONDS)).isTrue();
                  bothHeld.countDown();
                  assertThat(bothHeld.await(10, TimeUnit.SECONDS)).isTrue();
                  batching.unlock();
                } catch (Throwable e) {
                  errors.add(e);
                }
              });
      threads.add(t);
      t.start();
    }
    for (Thread t : threads) {
      while (t.getState() != Thread.State.TIMED_WAITING) {
        Thread.sleep(5);
      }
    }
    raw.unlock();
    for (Thread t : threads) {
      t.join(15000);
    }

    assertThat(errors).isEmpty();
    // One acquisition and release by this test, one for the whole batch.
    assertThat(raw.locks.get()).isEqualTo(2);
    assertThat(raw.unlocks.get()).isEqualTo(2);
  }

  @Test
  public void batch_supplierIsOnlyConsultedBetweenBatches() throws Exception {
    TestLock a = new TestLock();
    TestLock b = new TestLock();
    TestLock[] current = {a};
    Supplier<Lock> supplier = () -> current[0];
    Lock batching = H2CustomLockAccountPatchReviewStore.newBatchingLock(supplier, 1);

    assertThat(batching.tryLock(1, TimeUnit.SECONDS)).isTrue();
    current[0] = b;
    batching.unlock();

    assertThat(a.locks.get()).isEqualTo(1);
    assertThat(a.unlocks.get()).isEqualTo(1);
    assertThat(b.locks.get()).isEqualTo(0);
    assertThat(b.unlocks.get()).isEqualTo(0);

    assertThat(batching.tryLock(1, TimeUnit.SECONDS)).isTrue();
    batching.unlock();

    assertThat(b.locks.get()).isEqualTo(1);
    assertThat(b.unlocks.get()).isEqualTo(1);
    assertThat(a.locks.get()).isEqualTo(1);
  }

  @Test
  public void beforeStopPlugin_waitsForInFlightBatchBeforeReturning() throws Exception {
    FakeLockManager manager = new FakeLockManager();
    var unused = lockManagerItem.set(manager, "plugin");
    H2PluginLockAccountPatchReviewStore store = newStore();
    store.start();

    var connection = store.getConnection();
    AtomicBoolean returned = new AtomicBoolean();
    Thread stopper =
        new Thread(
            () -> {
              store.beforeStopPlugin(plugin("plugin"));
              returned.set(true);
            });
    stopper.start();
    while (stopper.getState() != Thread.State.WAITING) {
      Thread.sleep(5);
    }
    assertThat(returned.get()).isFalse();
    assertThat(manager.lock.unlocks.get()).isEqualTo(manager.lock.locks.get() - 1);

    connection.close();
    stopper.join(5000);

    assertThat(returned.get()).isTrue();
    assertThat(manager.lock.unlocks.get()).isEqualTo(manager.lock.locks.get());
  }

  @Test
  public void batch_awaitIdleWaitsForActiveBatch() throws Exception {
    TestLock raw = new TestLock();
    H2CustomLockAccountPatchReviewStore.BatchingLock batching =
        H2CustomLockAccountPatchReviewStore.newBatchingLock(() -> raw, 1);

    batching.awaitIdle();
    assertThat(batching.tryLock(1, TimeUnit.SECONDS)).isTrue();
    AtomicBoolean idle = new AtomicBoolean();
    Thread waiter =
        new Thread(
            () -> {
              try {
                batching.awaitIdle();
                idle.set(true);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            });
    waiter.start();
    while (waiter.getState() != Thread.State.WAITING) {
      Thread.sleep(5);
    }

    assertThat(idle.get()).isFalse();
    batching.unlock();
    waiter.join(5000);
    assertThat(idle.get()).isTrue();
  }

  private static Plugin plugin(String name) {
    Plugin plugin = Mockito.mock(Plugin.class);
    Mockito.when(plugin.getName()).thenReturn(name);
    return plugin;
  }

  private H2PluginLockAccountPatchReviewStore newStore() {
    return new H2PluginLockAccountPatchReviewStore(cfg, sitePaths, lockManagerItem);
  }

  private static class FakeLockManager implements LockManager {
    final List<LockKey> requestedKeys = new CopyOnWriteArrayList<>();
    final TestLock lock = new TestLock();

    @Override
    public Lock getLock(String name) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Lock getLock(LockKey key) {
      requestedKeys.add(key);
      return lock;
    }
  }

  /** Lock that, like a distributed lock, may be released by any thread. */
  private static class TestLock implements Lock {
    final Semaphore semaphore = new Semaphore(1);
    final AtomicInteger locks = new AtomicInteger();
    final AtomicInteger unlocks = new AtomicInteger();

    @Override
    public boolean tryLock() {
      if (semaphore.tryAcquire()) {
        locks.incrementAndGet();
        return true;
      }
      return false;
    }

    @Override
    public void unlock() {
      unlocks.incrementAndGet();
      semaphore.release();
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
    public boolean tryLock(long time, TimeUnit unit) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Condition newCondition() {
      throw new UnsupportedOperationException();
    }
  }
}
