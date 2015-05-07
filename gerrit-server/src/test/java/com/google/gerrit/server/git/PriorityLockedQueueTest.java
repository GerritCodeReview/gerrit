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

import org.jmock.lib.concurrent.DeterministicScheduler;
import org.junit.Test;

import java.util.Set;

import autovalue.shaded.com.google.common.common.collect.Sets;


class PriorityLockedQueueTest {

  private final Set<String> processedEntries;
  DeterministicScheduler scheduler;
  TestPriorityLockedQueue testPriorityLockedQueue;

  PriorityLockedQueueTest() {
    DeterministicScheduler scheduler = new DeterministicScheduler();
    processedEntries = Sets.newHashSet();
    new PriorityLockedQueue<String> {

    }
  }

  @Test
  public void testOneProcess() {

  }

  @Test
  public void testSchedule() {
       context.checking(new Expectations() {{
      door.unlock();
  }});
  }

  @Test
  public void testParallel() {

  }

  @Test
  public void testSameResource() {

  }

  @Test
  public void TestParallelWithManyResources() {

  }

  @Test
  public void testLoadedScheduleManyJobsOneResource() {

  }

/*
  Door door = context.mock(Door.class);
  Alarm alarm = context.mock(Alarm.class);



  TimeLock lock = new TimeLock(door, alarm);

  @Test
  public void locksAndUnlocksDoorOnRegularScheduleAndSoundsAlarmBeforeUnlockingDoor() {

    // Configure the behaviour of the lock
    lock.setOpenFor(1000);
    lock.setClosedFor(2000);
    lock.setSoundsAlarmBeforeClosingFor(100);

    context.checking(new Expectations() {{
        door.unlock();
    }});
    lock.activate();

    context.checking(new Expectations() {{
        alarm.start();
    }});
    scheduler.tick(900);

    context.checking(new Expectations() {{
        alarm.stop();
        door.lock();
    }});
    scheduler.tick(100);

    context.checking(new Expectations() {{
        door.unlock();
    }});
    scheduler.tick(2000);
  }*/

  class TestPriorityLockedQueue extends PriorityLockedQueue<String> {
    @Override
    void process(ResourceTask<String> task) {
      new Thread(group, target)
      for (String s : task.resources()) {
        Thread.sleep(100); // assume
        processedEntries.add(s);
      }
    }
  }
}
