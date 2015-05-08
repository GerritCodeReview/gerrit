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

import com.google.common.collect.Sets;

import org.jmock.lib.concurrent.DeterministicScheduler;
import org.junit.Test;

import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PriorityLockedQueueTest {

  Set<String> processedEntries;
  ScheduledExecutorService scheduler;
  TestPriorityLockedQueue testPriorityLockedQueue;

  // This test is a lot about timing, except for testing the processOneTask test
  // which would lock up
  boolean noBlocking;

  public PriorityLockedQueueTest() {
    //scheduler = new DeterministicScheduler();
    //scheduler = new ScheduledExecutorService();
    scheduler = Executors.newSingleThreadScheduledExecutor();

    testPriorityLockedQueue = new TestPriorityLockedQueue(scheduler);
    processedEntries = Sets.newHashSet();
    noBlocking = false;
  }

  @Test
  public void testOneProcess() throws Exception {
    // We need to start the test in its own thread and in this thread we will
    // just control the time, so we don't lock up ourselves.
    noBlocking = true;
    assertThat(testPriorityLockedQueue.processItem(new ResourceTask<String>() {
      @Override
      public Set<String> resources() {
        return Sets.newHashSet("A");
      }
    })).isEqualTo(true);
    noBlocking = false;

    assertThat(processedEntries).contains("A");
  }

  @Test
  public void testSchedule() throws Exception {
    testPriorityLockedQueue.schedule(new ResourceTask<String>() {
      @Override
      public Set<String> resources() {
        return Sets.newHashSet("B");
      }
    }, 0);
    //scheduler.tick(2, TimeUnit.SECONDS);
    //scheduler.wait(2000);
    tick(2000);
    assertThat(processedEntries).contains("B");
  }

  synchronized void tick(long ms) throws InterruptedException {
    this.wait(ms);
  }

  @Test
  public void testParallel() throws Exception {
    testPriorityLockedQueue.schedule(new ResourceTask<String>() {
      @Override
      public Set<String> resources() {
        return Sets.newHashSet("C");
      }
    }, 0);
    testPriorityLockedQueue.schedule(new ResourceTask<String>() {
      @Override
      public Set<String> resources() {
        return Sets.newHashSet("D");
      }
    }, 10);

    tick(2000);
    assertThat(processedEntries).contains("C");
    assertThat(processedEntries).contains("D");
  }

  @Test
  public void testSameResource() throws Exception {
    testPriorityLockedQueue.schedule(new ResourceTask<String>() {
      @Override
      public Set<String> resources() {
        return Sets.newHashSet("E");
      }
    }, 0);
    testPriorityLockedQueue.schedule(new ResourceTask<String>() {
      @Override
      public Set<String> resources() {
        return Sets.newHashSet("E");
      }
    }, 500); // milliseconds!

    // pass just as much time we'll be sure F has finished, but not enough,
    // such that sequential scheduling would have worked
    tick(1600);
    assertThat(processedEntries).contains("E");
  }

  @Test
  public void TestParallelWithManyResources() throws Exception {
    testPriorityLockedQueue.schedule(new ResourceTask<String>() {
      @Override
      public Set<String> resources() {
        return Sets.newHashSet("G", "H", "I");
      }
    }, 0);
    testPriorityLockedQueue.schedule(new ResourceTask<String>() {
      @Override
      public Set<String> resources() {
        return Sets.newHashSet("J", "K", "L");
      }
    }, 500); // milliseconds!
    tick(1600);
    assertThat(processedEntries).contains("G");
    assertThat(processedEntries).contains("H");
    assertThat(processedEntries).contains("I");
    assertThat(processedEntries).contains("J");
    assertThat(processedEntries).contains("K");
    assertThat(processedEntries).contains("L");
  }

  @Test
  public void testLoadedScheduleManyJobsOneResource() throws Exception {

  }


  class TestPriorityLockedQueue extends PriorityLockedQueue<String> {
    TestPriorityLockedQueue(ScheduledExecutorService sched) {
      super(sched);
    }
    @Override
    void process(final ResourceTask<String> task) {
      if (noBlocking) {
        processedEntries.addAll(task.resources());
        processed(task);
      } else {
        // Assume any task given to us takes 1 second to process
        scheduler.schedule(new Runnable() {
          @Override
          public void run() {
            processedEntries.addAll(task.resources());
            processed(task);
          }
        }, 100, TimeUnit.MILLISECONDS);
      }
    }
  }
}

