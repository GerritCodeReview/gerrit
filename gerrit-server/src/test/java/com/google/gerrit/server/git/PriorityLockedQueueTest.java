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
// TODO(sbeller): replace with jmock once it brings all the features we need
// esp. including https://github.com/jmock-developers/jmock-library/pull/75
import com.google.gerrit.server.git.concurrent.DeterministicScheduler;

import org.junit.Test;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class PriorityLockedQueueTest {
  Map<String, AtomicInteger> processedEntries;
  DeterministicScheduler scheduler;
  PriorityLockedQueueUnderTest testPriorityLockedQueue;

  public PriorityLockedQueueTest() {
    scheduler = new DeterministicScheduler();
    testPriorityLockedQueue = new PriorityLockedQueueUnderTest(scheduler);
    processedEntries = Maps.newHashMap();
  }

  @Test
  public void testOneProcess() throws Exception {

    final TestingTask task1 = new TestingTask(Sets.newHashSet("A"), false);
    scheduler.schedule(new Runnable() {
        @Override
      public void run() {
        // check we cannot have another task locking the resource while the first
        // is still running
        assertThat(testPriorityLockedQueue.locks.lock(Sets.newHashSet("A"))).isEqualTo(false);
      }
    }, 50, TimeUnit.MILLISECONDS);


    assertThat(testPriorityLockedQueue.process(task1)).isEqualTo(true);
    assertThat(processedEntries).containsKey("A");

    scheduler.tick(50, TimeUnit.MILLISECONDS);

    // check the lock after the fact:
    assertThat(testPriorityLockedQueue.locks.lock(Sets.newHashSet("A"))).isEqualTo(true);
    testPriorityLockedQueue.locks.unlock(Sets.newHashSet("A"));
  }


  @Test
  public void testParallelScheduling() throws Exception {
    final TestingTask task1 = new TestingTask(Sets.newHashSet("B"), false);
    final TestingTask task2 = new TestingTask(Sets.newHashSet("B", "C"), false);
    final TestingTask task3 = new TestingTask(Sets.newHashSet("C"), false);

    testPriorityLockedQueue.schedule(task1, 0);

    scheduler.tick(1, TimeUnit.MILLISECONDS);

    assertThat(testPriorityLockedQueue.process(task2)).isEqualTo(false);
    // the next command moves time forward by a 100ms as it's processing
    // in this thread
    assertThat(testPriorityLockedQueue.process(task3)).isEqualTo(true);

    // Now task2 should meet its resource constraints
    assertThat(testPriorityLockedQueue.process(task2)).isEqualTo(true);
  }

  /*
  @Test
  public void testParallel() throws Exception {
    final Semaphore waitonC = new Semaphore(0);
    final Semaphore waitonD = new Semaphore(0);
    new Thread(new Runnable() {
      @Override
      public void run() {
        testPriorityLockedQueue.schedule(new PriorityLockedQueue.Task<String>() {
          @Override
          public Set<String> resources() {
            return Sets.newHashSet("C");
          }
        }, 0);
        waitonC.release(1);
      }
    }).start();
    new Thread(new Runnable() {
      @Override
      public void run() {
        testPriorityLockedQueue.schedule(new PriorityLockedQueue.Task<String>() {
          @Override
          public Set<String> resources() {
            return Sets.newHashSet("D");
          }
        }, 0);
        waitonD.release(1);
      }
    }).start();
    waitonC.acquire();
    waitonD.acquire();

    scheduler.tick(110, TimeUnit.MILLISECONDS);
    assertThat(processedEntries).containsKey("C");
    assertThat(processedEntries).containsKey("D");
  }

  @Test
  public void testSameResource() throws Exception {
    testPriorityLockedQueue.schedule(new PriorityLockedQueue.Task<String>() {
      @Override
      public Set<String> resources() {
        return Sets.newHashSet("E");
      }
    }, 0);
    testPriorityLockedQueue.schedule(new PriorityLockedQueue.Task<String>() {
      @Override
      public Set<String> resources() {
        return Sets.newHashSet("E", "F");
      }
    }, 50); // milliseconds!

    // pass just as much time we'll be sure F has finished, but not enough,
    // such that sequential scheduling would have worked
    scheduler.tick(2, TimeUnit.SECONDS);
    assertThat(processedEntries).containsKey("E");
    assertThat(processedEntries).containsKey("F");
  }

  @Test
  public void TestParallelWithManyResources() throws Exception {
    testPriorityLockedQueue.schedule(new PriorityLockedQueue.Task<String>() {
      @Override
      public Set<String> resources() {
        return Sets.newHashSet("G", "H", "I");
      }
    }, 0);
    testPriorityLockedQueue.schedule(new PriorityLockedQueue.Task<String>() {
      @Override
      public Set<String> resources() {
        return Sets.newHashSet("J", "K", "L");
      }
    }, 50); // milliseconds!

    scheduler.schedule(new Runnable() {
        @Override
      public void run() {
          assertThat(processedEntries).containsKey("G");
          assertThat(processedEntries).containsKey("H");
          assertThat(processedEntries).containsKey("I");
          assertThat(processedEntries).containsKey("J");
          assertThat(processedEntries).containsKey("K");
          assertThat(processedEntries).containsKey("L");
      }
    }, 160, TimeUnit.MILLISECONDS);

    scheduler.tick(2, TimeUnit.SECONDS);
    assertThat(processedEntries).containsKey("G");
    assertThat(processedEntries).containsKey("H");
    assertThat(processedEntries).containsKey("I");
    assertThat(processedEntries).containsKey("J");
    assertThat(processedEntries).containsKey("K");
    assertThat(processedEntries).containsKey("L");
  }
*/

  class TestingTask implements PriorityLockedQueue.Task<String> {
    Set<String> resources;

    boolean fail;

    TestingTask(Set<String> resources, boolean shouldFailOnFirstTry) {
      this.resources = resources;
      this.fail = shouldFailOnFirstTry;
    }

    boolean shouldFail() {
      boolean ret = fail;
      fail = false;
      return ret;
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

    @Override
    void processTask(final TestingTask task) {
      for (String s : task.resources()) {
        if (processedEntries.get(s) == null) {
          processedEntries.put(s, new AtomicInteger());
        }
        processedEntries.get(s).addAndGet(1);
      }
      // As processTask takes place in the current thread, we'll
      // advance the time here
      scheduler.tick(100, TimeUnit.MILLISECONDS);
    }

    @Override
    void processAsyncTask(final TestingTask task) {
      if (!task.shouldFail()) {
        processTask(task);
        // Pretend we're working hard in another thread and schedule
        // a call to processingAsyncTaskDone when done.
        scheduler.schedule(new Runnable() {
          @Override
          public void run() {
            processingAsyncTaskDone(task);
          }
        }, 100, TimeUnit.MILLISECONDS);
      }
    }
  }
}

