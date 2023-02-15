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
import com.google.gerrit.metrics.Counter0;
import com.google.gerrit.metrics.Counter1;
import com.google.gerrit.metrics.Counter2;
import com.google.gerrit.metrics.Counter3;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.DisabledMetricMaker;
import com.google.gerrit.metrics.Field;
import com.google.inject.Singleton;
import java.util.concurrent.ConcurrentHashMap;
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

  public long getCount(String counter0Name) {
    return get(CounterKey.create(counter0Name)).longValue();
  }

  public long getCount(String counter1Name, Object field1Value) {
    return get(CounterKey.create(counter1Name, field1Value)).longValue();
  }

  public long getCount(String counter1Name, Object field1Value, Object field2Value) {
    return get(CounterKey.create(counter1Name, field1Value, field2Value)).longValue();
  }

  public long getCount(
      String counter1Name, Object field1Value, Object field2Value, Object field3Value) {
    return get(CounterKey.create(counter1Name, field1Value, field2Value, field3Value)).longValue();
  }

  public void reset() {
    counts.clear();
  }

  private MutableLong get(CounterKey counterKey) {
    return counts.computeIfAbsent(counterKey, key -> new MutableLong(0));
  }

  @Override
  public Counter0 newCounter(String name, Description desc) {
    return new Counter0() {
      @Override
      public void incrementBy(long value) {
        get(CounterKey.create(name)).add(value);
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
        get(CounterKey.create(name, field1)).add(value);
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
        get(CounterKey.create(name, field1, field2)).add(value);
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
        get(CounterKey.create(name, field1, field2, field3)).add(value);
      }

      @Override
      public void remove() {}
    };
  }

  @AutoValue
  abstract static class CounterKey {
    abstract String name();

    abstract ImmutableList<Object> fieldValues();

    static CounterKey create(String name) {
      return new AutoValue_TestMetricMaker_CounterKey(name, ImmutableList.of());
    }

    static CounterKey create(String name, Object field1Value) {
      return new AutoValue_TestMetricMaker_CounterKey(name, ImmutableList.of(field1Value));
    }

    static CounterKey create(String name, Object field1Value, Object field2Value) {
      return new AutoValue_TestMetricMaker_CounterKey(
          name, ImmutableList.of(field1Value, field2Value));
    }

    static CounterKey create(
        String name, Object field1Value, Object field2Value, Object field3Value) {
      return new AutoValue_TestMetricMaker_CounterKey(
          name, ImmutableList.of(field1Value, field2Value, field3Value));
    }
  }
}
