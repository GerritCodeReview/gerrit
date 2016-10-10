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
import static org.junit.Assert.fail;

import com.google.common.collect.Lists;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.testutil.TestTimeUtil;

import org.junit.Before;
import org.junit.Test;

import java.sql.Timestamp;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class EventSorterTest {
  private class TestEvent extends Event {
    protected TestEvent(Timestamp when) {
      super(
          new PatchSet.Id(new Change.Id(1), 1),
          new Account.Id(1000), new Account.Id(1000),
          when, changeCreatedOn, null);
    }

    @Override
    boolean uniquePerUpdate() {
      return false;
    }

    @Override
    void apply(ChangeUpdate update) {
      throw new UnsupportedOperationException();
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

    List<Event> events = events(e2, e1, e3);
    new EventSorter(events).sort();
    assertThat(events).containsExactly(e1, e2, e3).inOrder();
  }

  @Test
  public void topoSortNoChange() {
    Event e1 = new TestEvent(TimeUtil.nowTs());
    Event e2 = new TestEvent(TimeUtil.nowTs());
    Event e3 = new TestEvent(TimeUtil.nowTs());
    e2.addDep(e1);

    List<Event> events = events(e2, e1, e3);
    new EventSorter(events).sort();
    assertThat(events).containsExactly(e1, e2, e3).inOrder();
  }

  @Test
  public void topoSortOneDep() {
    Event e1 = new TestEvent(TimeUtil.nowTs());
    Event e2 = new TestEvent(TimeUtil.nowTs());
    Event e3 = new TestEvent(TimeUtil.nowTs());
    e1.addDep(e2);

    List<Event> events = events(e2, e3, e1);
    new EventSorter(events).sort();
    assertThat(events).containsExactly(e2, e1, e3).inOrder();
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

    List<Event> events = events(e1, e2, e3, e4);
    new EventSorter(events).sort();
    assertThat(events).containsExactly(e4, e3, e2, e1).inOrder();
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
    List<Event> events = events(e2, e3, e1, e4);
    new EventSorter(events).sort();
    assertThat(events).containsExactly(e3, e2, e4, e1).inOrder();
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
    List<Event> events = events(e4, e3, e2, e1);
    new EventSorter(events).sort();
    assertThat(events).containsExactly(e4, e1, e2, e3).inOrder();
  }

  @Test
  public void topoSortCycle() {
    Event e1 = new TestEvent(TimeUtil.nowTs());
    Event e2 = new TestEvent(TimeUtil.nowTs());

    // Implementation is not really defined, but infinite looping would be bad.
    // According to current implementation details, 2 pops 1, 1 pops 2 which was
    // already seen.
    List<Event> events = events(e2, e1);
    new EventSorter(events).sort();
    assertThat(events).containsExactly(e1, e2).inOrder();
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

  private List<Event> events(Event... es) {
    return Lists.newArrayList(es);
  }
}
