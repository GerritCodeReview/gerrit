// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.server.git;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gerrit.common.MultiLock;

import org.junit.Test;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

public class PriorityLockedQueueTest {

  Map<String, AtomicInteger> processedEntries;
  ScheduledExecutorService scheduler;
  PriorityLockedQueueUnderTest testPriorityLockedQueue;

  public PriorityLockedQueueTest() {
    scheduler = Executors.newSingleThreadScheduledExecutor();
    testPriorityLockedQueue = new PriorityLockedQueueUnderTest(scheduler);
    processedEntries = Maps.newHashMap();
  }

  @Test
  public void testOneProcessLockingDuringProcessing() throws Exception {
    final TestingTask task1 = new TestingTask(Sets.newHashSet("A"), false, 0, 1);
    new Thread(new Runnable() {
      @Override
      public void run() {
        task1.awaitBefore();
        MultiLock<String> lock = testPriorityLockedQueue.locks();
        assertThat(lock.lock(Sets.newHashSet("A"))).isFalse();
        task1.countDownAfter();
      }
    }).start();

    boolean result = testPriorityLockedQueue.process(task1);
    assertThat(result).isTrue();
    assertThat(processedEntries).containsKey("A");
    assertThat(testPriorityLockedQueue.locks.lock(Sets.newHashSet("A"))).isTrue();
  }

  @Test
  public void testOneProcessLockingDuringScheduling() throws Exception {
    final TestingTask task1 = new TestingTask(Sets.newHashSet("B"), false, 0, 1);

    new Thread(new Runnable() {
      @Override
      public void run() {
        task1.awaitBefore();
        // lock must be there while processing B
        MultiLock<String> locks = testPriorityLockedQueue.locks();
        assertThat(locks.lock(Sets.newHashSet("B"))).isFalse();
        task1.countDownAfter();
      }
    }).start();

    testPriorityLockedQueue.schedule(task1, 25);

    // lock must be free before processing B
    MultiLock<String> locks = testPriorityLockedQueue.locks();
    assertThat(locks.lock(Sets.newHashSet("B"))).isTrue();
    locks.unlock(Sets.newHashSet("B"));

    task1.awaitAfter();

    // lock must be free after processing B
    assertThat(locks.lock(Sets.newHashSet("B"))).isTrue();
    locks.unlock(Sets.newHashSet("B"));
  }

  @Test
  public void testParallelScheduling() throws Exception {
    final TestingTask task1 = new TestingTask(Sets.newHashSet("C"), false, 0, 1);
    final TestingTask task2 = new TestingTask(Sets.newHashSet("D"), false, 0, 1);

    testPriorityLockedQueue.schedule(task1, 1);
    testPriorityLockedQueue.schedule(task2, 1);

    task1.awaitBefore();
    task2.awaitBefore();
    // both are processing now at the same time
    task1.countDownAfter();
    task2.countDownAfter();
    task1.awaitAfter();
    task2.awaitAfter();
    assertThat(processedEntries).containsKey("C");
    assertThat(processedEntries).containsKey("D");
  }


  @Test
  public void testResourceConflict() throws Exception {
    final TestingTask task1 = new TestingTask(Sets.newHashSet("E", "F"), false, 0, 1);
    final TestingTask task2 = new TestingTask(Sets.newHashSet("E", "G"), false, 0, 0);

    testPriorityLockedQueue.schedule(task1, 1);
    task1.awaitBefore();
    assertThat(testPriorityLockedQueue.process(task2)).isFalse();
    task1.countDownAfter();
    task1.awaitAfter();
    assertThat(testPriorityLockedQueue.process(task2)).isTrue();

    assertThat(processedEntries).containsKey("E");
    assertThat(processedEntries.get("E").get()).isEqualTo(2);
    assertThat(processedEntries).containsKey("F");
    assertThat(processedEntries).containsKey("G");
  }

  class TestingTask implements PriorityLockedQueue.Task<String> {
    Set<String> resources;

    CountDownLatch before;
    CountDownLatch after;

    boolean fail;

    TestingTask(Set<String> resources, boolean shouldFailOnFirstTry,
        int in, int out) {
      this.resources = resources;
      this.fail = shouldFailOnFirstTry;
      this.before = new CountDownLatch(in + 1);
      this.after = new CountDownLatch(out + 1);
    }

    boolean shouldFail() {
      boolean ret = fail;
      fail = false;
      return ret;
    }

    void awaitBefore() {
      try {
        before.await();
      } catch (InterruptedException e) {
        e.printStackTrace();
        throw new RuntimeException(e);
      }
    }

    void countDownBefore() {
      before.countDown();
    }

    void awaitAfter() {
      try {
        after.await();
      } catch (InterruptedException e) {
        e.printStackTrace();
        throw new RuntimeException(e);
      }
    }

    void countDownAfter() {
      after.countDown();
    }

    @Override
    public Set<String> resources() {
      return resources;
    }
  }

  /**
   * For testing we just subclass the PriorityLockedQueue and a task
   * always takes 100 milliseconds to process.
   */
  class PriorityLockedQueueUnderTest extends
      PriorityLockedQueue<String, TestingTask> {
    PriorityLockedQueueUnderTest(ScheduledExecutorService sched) {
      super(sched, 250);
    }

    MultiLock<String> locks() {
      return locks;
    }

    void processTestingTask(final TestingTask task) {
      synchronized (processedEntries) {
        for (String s : task.resources()) {
          if (processedEntries.get(s) == null) {
            processedEntries.put(s, new AtomicInteger());
          }
          processedEntries.get(s).addAndGet(1);
        }
      }
    }

    @Override
    void processTask(final TestingTask task) {
      task.countDownBefore();
      task.awaitBefore();
      processTestingTask(task);
      task.countDownAfter();
      task.awaitAfter();
    }

    @Override
    void processAsyncTask(final TestingTask task) {
      new Thread(new Runnable(){
        @Override
        public void run() {
          task.countDownBefore();
          task.awaitBefore();
          if (!task.shouldFail()) {
            processTestingTask(task);
            processingAsyncTaskDone(task);
          }
          task.countDownAfter();
          task.awaitAfter();
          processingAsyncTaskDone(task);
        }
      }).start();
    }
  }
}

