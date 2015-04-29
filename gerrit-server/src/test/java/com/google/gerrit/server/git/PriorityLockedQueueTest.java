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
    assertThat(testPriorityLockedQueue.processTask(new PriorityLockedQueue.Task<String>() {
      @Override
      public Set<String> resources() {
        return Sets.newHashSet("A");
      }
    })).isEqualTo(true);
    assertThat(processedEntries).containsKey("A");
  }

  @Test
  public void testSchedule() throws Exception {
    testPriorityLockedQueue.schedule(new PriorityLockedQueue.Task<String>() {
      @Override
      public Set<String> resources() {
        return Sets.newHashSet("B");
      }
    }, 0);
    scheduler.tick(2, TimeUnit.SECONDS);
    assertThat(processedEntries).containsKey("B");
  }

  @Test
  public void testParallel() throws Exception {
    testPriorityLockedQueue.schedule(new PriorityLockedQueue.Task<String>() {
      @Override
      public Set<String> resources() {
        return Sets.newHashSet("C");
      }
    }, 0);
    testPriorityLockedQueue.schedule(new PriorityLockedQueue.Task<String>() {
      @Override
      public Set<String> resources() {
        return Sets.newHashSet("D");
      }
    }, 10);

    scheduler.tick(2, TimeUnit.SECONDS);
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

  class PriorityLockedQueueUnderTest extends PriorityLockedQueue<String, PriorityLockedQueue.Task<String>> {
    PriorityLockedQueueUnderTest(ScheduledExecutorService sched) {
      super(sched);
    }

    @Override
    void process(final PriorityLockedQueue.Task<String> task) {
      for (String s : task.resources()) {
        if (processedEntries.get(s) == null) {
          processedEntries.put(s, new AtomicInteger());
        }
        processedEntries.get(s).addAndGet(1);
      }
    }
  }
}

