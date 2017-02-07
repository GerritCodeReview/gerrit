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

package com.google.gerrit.metrics.proc;

import static com.google.common.truth.Truth.assertThat;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.google.gerrit.common.Version;
import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.metrics.CallbackMetric0;
import com.google.gerrit.metrics.Counter0;
import com.google.gerrit.metrics.Counter1;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Description.FieldOrdering;
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.metrics.dropwizard.DropWizardMetricMaker;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class ProcMetricModuleTest {
  @Rule public ExpectedException exception = ExpectedException.none();

  @Inject MetricMaker metrics;

  @Inject MetricRegistry registry;

  @Test
  public void testConstantBuildLabel() {
    Gauge<String> buildLabel = gauge("build/label");
    assertThat(buildLabel.getValue()).isEqualTo(Version.getVersion());
  }

  @Test
  public void testProcUptime() {
    Gauge<Long> birth = gauge("proc/birth_timestamp");
    assertThat(birth.getValue())
        .isAtMost(TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis()));

    Gauge<Long> uptime = gauge("proc/uptime");
    assertThat(uptime.getValue()).isAtLeast(1L);
  }

  @Test
  public void testCounter0() {
    Counter0 cntr =
        metrics.newCounter("test/count", new Description("simple test").setCumulative());

    Counter raw = get("test/count", Counter.class);
    assertThat(raw.getCount()).isEqualTo(0);

    cntr.increment();
    assertThat(raw.getCount()).isEqualTo(1);

    cntr.incrementBy(5);
    assertThat(raw.getCount()).isEqualTo(6);
  }

  @Test
  public void testCounter1() {
    Counter1<String> cntr =
        metrics.newCounter(
            "test/count", new Description("simple test").setCumulative(), Field.ofString("action"));

    Counter total = get("test/count_total", Counter.class);
    assertThat(total.getCount()).isEqualTo(0);

    cntr.increment("passed");
    Counter passed = get("test/count/passed", Counter.class);
    assertThat(total.getCount()).isEqualTo(1);
    assertThat(passed.getCount()).isEqualTo(1);

    cntr.incrementBy("failed", 5);
    Counter failed = get("test/count/failed", Counter.class);
    assertThat(total.getCount()).isEqualTo(6);
    assertThat(passed.getCount()).isEqualTo(1);
    assertThat(failed.getCount()).isEqualTo(5);
  }

  @Test
  public void testCounterPrefixFields() {
    Counter1<String> cntr =
        metrics.newCounter(
            "test/count",
            new Description("simple test")
                .setCumulative()
                .setFieldOrdering(FieldOrdering.PREFIX_FIELDS_BASENAME),
            Field.ofString("action"));

    Counter total = get("test/count_total", Counter.class);
    assertThat(total.getCount()).isEqualTo(0);

    cntr.increment("passed");
    Counter passed = get("test/passed/count", Counter.class);
    assertThat(total.getCount()).isEqualTo(1);
    assertThat(passed.getCount()).isEqualTo(1);

    cntr.incrementBy("failed", 5);
    Counter failed = get("test/failed/count", Counter.class);
    assertThat(total.getCount()).isEqualTo(6);
    assertThat(passed.getCount()).isEqualTo(1);
    assertThat(failed.getCount()).isEqualTo(5);
  }

  @Test
  public void testCallbackMetric0() {
    final CallbackMetric0<Long> cntr =
        metrics.newCallbackMetric(
            "test/count", Long.class, new Description("simple test").setCumulative());

    final AtomicInteger invocations = new AtomicInteger(0);
    metrics.newTrigger(
        cntr,
        new Runnable() {
          @Override
          public void run() {
            invocations.getAndIncrement();
            cntr.set(42L);
          }
        });

    // Triggers run immediately with DropWizard binding.
    assertThat(invocations.get()).isEqualTo(1);

    Gauge<Long> raw = gauge("test/count");
    assertThat(raw.getValue()).isEqualTo(42);

    // Triggers are debounced to avoid being fired too frequently.
    assertThat(invocations.get()).isEqualTo(1);
  }

  @Test
  public void testInvalidName1() {
    exception.expect(IllegalArgumentException.class);
    metrics.newCounter("invalid name", new Description("fail"));
  }

  @Test
  public void testInvalidName2() {
    exception.expect(IllegalArgumentException.class);
    metrics.newCounter("invalid/ name", new Description("fail"));
  }

  @SuppressWarnings({"unchecked", "cast"})
  private <V> Gauge<V> gauge(String name) {
    return (Gauge<V>) get(name, Gauge.class);
  }

  private <M extends Metric> M get(String name, Class<M> type) {
    Metric m = registry.getMetrics().get(name);
    assertThat(m).named(name).isNotNull();
    assertThat(m).named(name).isInstanceOf(type);

    @SuppressWarnings("unchecked")
    M result = (M) m;
    return result;
  }

  @Before
  public void setup() {
    Injector injector = Guice.createInjector(new DropWizardMetricMaker.ApiModule());

    LifecycleManager mgr = new LifecycleManager();
    mgr.add(injector);
    mgr.start();

    injector.injectMembers(this);
  }
}
