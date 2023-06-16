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

import com.google.gerrit.common.UsedAt;
import com.google.gerrit.metrics.Counter0;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.DisabledMetricMaker;
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.Timer1;
import com.google.inject.Singleton;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.mutable.MutableLong;

/**
 * {@link com.google.gerrit.metrics.MetricMaker} to be bound in tests.
 *
 * <p>Records how often {@link Counter0} and {@link Timer1} metrics are invoked. Metrics for other
 * types are not recorded.
 *
 * <p>Allows test to check how much a {@link Counter0} and {@link Timer1} metric is increased by an
 * operation.
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
  private final ConcurrentHashMap<String, MutableLong> counts = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, MutableLong> timers = new ConcurrentHashMap<>();

  public long getCount(String counter0Name) {
    return getCounterValue(counter0Name).longValue();
  }

  public long getTimer(String timerName) {
    return getTimerValue(timerName).longValue();
  }

  public void reset() {
    counts.clear();
    timers.clear();
  }

  private MutableLong getCounterValue(String counter0Name) {
    return counts.computeIfAbsent(counter0Name, name -> new MutableLong(0));
  }

  private MutableLong getTimerValue(String timerName) {
    return counts.computeIfAbsent(timerName, name -> new MutableLong(0));
  }

  @Override
  public Counter0 newCounter(String name, Description desc) {
    return new Counter0() {
      @Override
      public void incrementBy(long value) {
        getCounterValue(name).add(value);
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
        getTimerValue(name).add(value);
      }

      @Override
      public void remove() {}
    };
  }
}
