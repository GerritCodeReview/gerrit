// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.acceptance;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.UsedAt;
import com.google.gerrit.metrics.Counter0;
import com.google.gerrit.metrics.Counter1;
import com.google.gerrit.metrics.Counter2;
import com.google.gerrit.metrics.Counter3;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.DisabledMetricMaker;
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.Timer1;
import com.google.inject.Singleton;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.mutable.MutableLong;

/**
 * {@link com.google.gerrit.metrics.MetricMaker} to be bound in tests.
 *
 * <p>Records how often counter metrics are invoked. Metrics of other types are not recorded.
 *
 * <p>Allows test to check how much a counter metrics is increased by an operation.
 *
 * <p>Example:
 *
 * <pre>
 * public class MyTest extends AbstractDaemonTest {
 *   {@literal @}Inject private TestMetricMaker testMetricMaker;
 *
 *   ...
 *
 *   {@literal @}Test
 *   public void testFoo() throws Exception {
 *     testMetricMaker.reset();
 *     doSomething();
 *     assertThat(testMetricMaker.getCount("foo/bar_count")).isEqualsTo(1);
 *   }
 * }
 * </pre>
 */
@Singleton
public class TestMetricMaker extends DisabledMetricMaker {
  private final ConcurrentHashMap<CounterKey, MutableLong> counts = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<CounterKey, MutableLong> timers = new ConcurrentHashMap<>();

  public long getCount(String counterName, Object... fieldValues) {
    return getCounterValue(CounterKey.create(counterName, fieldValues)).longValue();
  }

  public long getTimer(String timerName) {
    return getTimerValue(CounterKey.create(timerName)).longValue();
  }

  public void reset() {
    counts.clear();
    timers.clear();
  }

  private MutableLong getCounterValue(CounterKey counterKey) {
    return counts.computeIfAbsent(counterKey, name -> new MutableLong(0));
  }

  private MutableLong getTimerValue(CounterKey timerName) {
    return counts.computeIfAbsent(timerName, name -> new MutableLong(0));
  }

  @Override
  public Counter0 newCounter(String name, Description desc) {
    return new Counter0() {
      @Override
      public void incrementBy(long value) {
        getCounterValue(CounterKey.create(name)).add(value);
      }

      @Override
      public void remove() {}
    };
  }

  @Override
  @UsedAt(UsedAt.Project.PLUGIN_PULL_REPLICATION)
  public <F1> Timer1<F1> newTimer(String name, Description desc, Field<F1> field1) {
    return new Timer1<>(name, field1) {
      @Override
      protected void doRecord(F1 field1, long value, TimeUnit unit) {
        getTimerValue(CounterKey.create(name)).add(value);
      }

      @Override
      public void remove() {}
    };
  }

  @Override
  public <F1> Counter1<F1> newCounter(String name, Description desc, Field<F1> field1) {
    return new Counter1<>() {
      @Override
      public void incrementBy(F1 field1, long value) {
        getCounterValue(CounterKey.create(name, field1)).add(value);
      }

      @Override
      public void remove() {}
    };
  }

  @Override
  public <F1, F2> Counter2<F1, F2> newCounter(
      String name, Description desc, Field<F1> field1, Field<F2> field2) {
    return new Counter2<>() {
      @Override
      public void incrementBy(F1 field1, F2 field2, long value) {
        getCounterValue(CounterKey.create(name, field1, field2)).add(value);
      }

      @Override
      public void remove() {}
    };
  }

  @Override
  public <F1, F2, F3> Counter3<F1, F2, F3> newCounter(
      String name, Description desc, Field<F1> field1, Field<F2> field2, Field<F3> field3) {
    return new Counter3<>() {
      @Override
      public void incrementBy(F1 field1, F2 field2, F3 field3, long value) {
        getCounterValue(CounterKey.create(name, field1, field2, field3)).add(value);
      }

      @Override
      public void remove() {}
    };
  }

  @AutoValue
  abstract static class CounterKey {
    abstract String name();

    abstract ImmutableList<Object> fieldValues();

    static CounterKey create(String name, Object... fieldValues) {
      return new AutoValue_TestMetricMaker_CounterKey(
          name, ImmutableList.copyOf(Arrays.asList(fieldValues)));
    }
  }
}
