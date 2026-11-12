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

import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Project;
import java.util.concurrent.CompletableFuture;
import org.junit.Test;

public class ChangeLookupLocksTest {
  private static final BranchNameKey MASTER = BranchNameKey.create(Project.nameKey("p"), "master");
  private static final BranchNameKey STABLE = BranchNameKey.create(Project.nameKey("p"), "stable");
  private static final BranchNameKey OTHER_PROJECT =
      BranchNameKey.create(Project.nameKey("q"), "master");

  private final ChangeLookupLocks locks = new ChangeLookupLocks();

  @Test
  public void sameKeyIsExclusive() throws Exception {
    ChangeLookupLocks.Handle held = locks.tryLock(MASTER, "I1", 0, MILLISECONDS);
    assertThat(held).isNotNull();

    assertThat(locks.tryLock(MASTER, "I1", 10, MILLISECONDS)).isNull();

    held.release();
    ChangeLookupLocks.Handle again = locks.tryLock(MASTER, "I1", 0, MILLISECONDS);
    assertThat(again).isNotNull();
    again.release();
  }

  @Test
  public void differentKeysBranchesAndProjectsDontShareLocks() throws Exception {
    ChangeLookupLocks.Handle held = locks.tryLock(MASTER, "I1", 0, MILLISECONDS);

    for (ChangeLookupLocks.Handle other :
        new ChangeLookupLocks.Handle[] {
          locks.tryLock(MASTER, "I2", 0, MILLISECONDS),
          locks.tryLock(STABLE, "I1", 0, MILLISECONDS),
          locks.tryLock(OTHER_PROJECT, "I1", 0, MILLISECONDS)
        }) {
      assertThat(other).isNotNull();
      other.release();
    }
    held.release();
  }

  @Test
  public void waiterGetsLockWhenReleasedFromAnotherThread() throws Exception {
    ChangeLookupLocks.Handle held = locks.tryLock(MASTER, "I1", 0, MILLISECONDS);
    CompletableFuture<ChangeLookupLocks.Handle> waiter =
        CompletableFuture.supplyAsync(
            () -> {
              try {
                return locks.tryLock(MASTER, "I1", 10, SECONDS);
              } catch (InterruptedException e) {
                throw new IllegalStateException(e);
              }
            });

    CompletableFuture.runAsync(held::release).get(10, SECONDS);

    ChangeLookupLocks.Handle acquired = waiter.get(10, SECONDS);
    assertThat(acquired).isNotNull();
    acquired.release();
  }

  @Test
  public void locksAreRemovedWhenUnused() throws Exception {
    ChangeLookupLocks.Handle held = locks.tryLock(MASTER, "I1", 0, MILLISECONDS);
    assertThat(locks.tryLock(MASTER, "I1", 0, MILLISECONDS)).isNull();
    assertThat(locks.size()).isEqualTo(1);

    held.release();
    held.release();
    assertThat(locks.size()).isEqualTo(0);
  }
}
