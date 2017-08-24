// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.notedb.rebuild;

import static com.google.common.truth.Truth.assertThat;
import static java.util.stream.Collectors.toList;
import static org.junit.Assert.fail;

import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.testutil.TestTimeUtil;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.Before;
import org.junit.Test;

public class EventSorterTest {
  private class TestEvent extends Event {
    protected TestEvent(Timestamp when) {
      super(
          new PatchSet.Id(new Change.Id(1), 1),
          new Account.Id(1000),
          new Account.Id(1000),
          when,
          changeCreatedOn,
          null);
    }

    @Override
    boolean uniquePerUpdate() {
      return false;
    }

    @Override
    void apply(ChangeUpdate update) {
      throw new UnsupportedOperationException();
    }

    @SuppressWarnings("deprecation")
    @Override
    public String toString() {
      return "E{" + when.getSeconds() + '}';
    }
  }

  private Timestamp changeCreatedOn;

  @Before
  public void setUp() {
    TestTimeUtil.resetWithClockStep(10, TimeUnit.SECONDS);
    changeCreatedOn = TimeUtil.nowTs();
  }

  @Test
  public void naturalSort() {
    Event e1 = new TestEvent(TimeUtil.nowTs());
    Event e2 = new TestEvent(TimeUtil.nowTs());
    Event e3 = new TestEvent(TimeUtil.nowTs());

    for (List<Event> events : Collections2.permutations(events(e1, e2, e3))) {
      assertSorted(events, events(e1, e2, e3));
    }
  }

  @Test
  public void topoSortOneDep() {
    List<Event> es;

    // Input list is 0,1,2

    // 0 depends on 1 => 1,0,2
    es = threeEventsOneDep(0, 1);
    assertSorted(es, events(es, 1, 0, 2));

    // 1 depends on 0 => 0,1,2
    es = threeEventsOneDep(1, 0);
    assertSorted(es, events(es, 0, 1, 2));

    // 0 depends on 2 => 1,2,0
    es = threeEventsOneDep(0, 2);
    assertSorted(es, events(es, 1, 2, 0));

    // 2 depends on 0 => 0,1,2
    es = threeEventsOneDep(2, 0);
    assertSorted(es, events(es, 0, 1, 2));

    // 1 depends on 2 => 0,2,1
    es = threeEventsOneDep(1, 2);
    assertSorted(es, events(es, 0, 2, 1));

    // 2 depends on 1 => 0,1,2
    es = threeEventsOneDep(2, 1);
    assertSorted(es, events(es, 0, 1, 2));
  }

  private List<Event> threeEventsOneDep(int depFromIdx, int depOnIdx) {
    List<Event> events =
        Lists.newArrayList(
            new TestEvent(TimeUtil.nowTs()),
            new TestEvent(TimeUtil.nowTs()),
            new TestEvent(TimeUtil.nowTs()));
    events.get(depFromIdx).addDep(events.get(depOnIdx));
    return events;
  }

  @Test
  public void lastEventDependsOnFirstEvent() {
    List<Event> events = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      events.add(new TestEvent(TimeUtil.nowTs()));
    }
    events.get(events.size() - 1).addDep(events.get(0));
    assertSorted(events, events);
  }

  @Test
  public void firstEventDependsOnLastEvent() {
    List<Event> events = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      events.add(new TestEvent(TimeUtil.nowTs()));
    }
    events.get(0).addDep(events.get(events.size() - 1));

    List<Event> expected = new ArrayList<>();
    expected.addAll(events.subList(1, events.size()));
    expected.add(events.get(0));
    assertSorted(events, expected);
  }

  @Test
  public void topoSortChainOfDeps() {
    Event e1 = new TestEvent(TimeUtil.nowTs());
    Event e2 = new TestEvent(TimeUtil.nowTs());
    Event e3 = new TestEvent(TimeUtil.nowTs());
    Event e4 = new TestEvent(TimeUtil.nowTs());
    e1.addDep(e2);
    e2.addDep(e3);
    e3.addDep(e4);

    assertSorted(events(e1, e2, e3, e4), events(e4, e3, e2, e1));
  }

  @Test
  public void topoSortMultipleDeps() {
    Event e1 = new TestEvent(TimeUtil.nowTs());
    Event e2 = new TestEvent(TimeUtil.nowTs());
    Event e3 = new TestEvent(TimeUtil.nowTs());
    Event e4 = new TestEvent(TimeUtil.nowTs());
    e1.addDep(e2);
    e1.addDep(e4);
    e2.addDep(e3);

    // Processing 3 pops 2, processing 4 pops 1.
    assertSorted(events(e2, e3, e1, e4), events(e3, e2, e4, e1));
  }

  @Test
  public void topoSortMultipleDepsPreservesNaturalOrder() {
    Event e1 = new TestEvent(TimeUtil.nowTs());
    Event e2 = new TestEvent(TimeUtil.nowTs());
    Event e3 = new TestEvent(TimeUtil.nowTs());
    Event e4 = new TestEvent(TimeUtil.nowTs());
    e1.addDep(e4);
    e2.addDep(e4);
    e3.addDep(e4);

    // Processing 4 pops 1, 2, 3 in natural order.
    assertSorted(events(e4, e3, e2, e1), events(e4, e1, e2, e3));
  }

  @Test
  public void topoSortCycle() {
    Event e1 = new TestEvent(TimeUtil.nowTs());
    Event e2 = new TestEvent(TimeUtil.nowTs());

    // Implementation is not really defined, but infinite looping would be bad.
    // According to current implementation details, 2 pops 1, 1 pops 2 which was
    // already seen.
    assertSorted(events(e2, e1), events(e1, e2));
  }

  @Test
  public void topoSortDepNotInInputList() {
    Event e1 = new TestEvent(TimeUtil.nowTs());
    Event e2 = new TestEvent(TimeUtil.nowTs());
    Event e3 = new TestEvent(TimeUtil.nowTs());
    e1.addDep(e3);

    List<Event> events = events(e2, e1);
    try {
      new EventSorter(events).sort();
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      // Expected.
    }
  }

  private static List<Event> events(Event... es) {
    return Lists.newArrayList(es);
  }

  private static List<Event> events(List<Event> in, Integer... indexes) {
    return Stream.of(indexes).map(in::get).collect(toList());
  }

  private static void assertSorted(List<Event> unsorted, List<Event> expected) {
    List<Event> actual = new ArrayList<>(unsorted);
    new EventSorter(actual).sort();
    assertThat(actual).named("sorted" + unsorted).isEqualTo(expected);
  }
}
