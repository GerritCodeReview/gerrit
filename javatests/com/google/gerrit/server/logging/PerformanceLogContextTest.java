// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.server.logging;

import static com.google.common.truth.Truth.assertThat;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.metrics.Timer0;
import com.google.gerrit.metrics.Timer1;
import com.google.gerrit.metrics.Timer2;
import com.google.gerrit.metrics.Timer3;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.testing.InMemoryModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.eclipse.jgit.lib.Config;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class PerformanceLogContextTest {
  @Inject @GerritServerConfig private Config config;
  @Inject private DynamicSet<PerformanceLogger> performanceLoggers;

  // In this test setup this gets the DisabledMetricMaker injected. This means it doesn't record any
  // metric, but performance log records are still created.
  @Inject private MetricMaker metricMaker;

  private TestPerformanceLogger testPerformanceLogger;
  private RegistrationHandle performanceLoggerRegistrationHandle;

  @Before
  public void setup() {
    Injector injector = Guice.createInjector(new InMemoryModule());
    injector.injectMembers(this);

    testPerformanceLogger = new TestPerformanceLogger();
    performanceLoggerRegistrationHandle = performanceLoggers.add("gerrit", testPerformanceLogger);
  }

  @After
  public void cleanup() {
    performanceLoggerRegistrationHandle.remove();

    LoggingContext.getInstance().clearPerformanceLogEntries();
    LoggingContext.getInstance().performanceLogging(false);
  }

  @Test
  public void traceTimersInsidePerformanceLogContextCreatePerformanceLog() {
    assertThat(LoggingContext.getInstance().isPerformanceLogging()).isFalse();
    assertThat(LoggingContext.getInstance().getPerformanceLogRecords()).isEmpty();

    try (PerformanceLogContext traceContext =
        new PerformanceLogContext(config, performanceLoggers)) {
      assertThat(LoggingContext.getInstance().isPerformanceLogging()).isTrue();

      TraceContext.newTimer("test1").close();
      TraceContext.newTimer("test2", "foo", "bar").close();
      TraceContext.newTimer("test3", "foo1", "bar1", "foo2", "bar2").close();
      TraceContext.newTimer("test4", "foo1", "bar1", "foo2", "bar2", "foo3", "bar3").close();
      TraceContext.newTimer("test5", "foo1", "bar1", "foo2", "bar2", "foo3", "bar3", "foo4", "bar4")
          .close();

      assertThat(LoggingContext.getInstance().getPerformanceLogRecords()).hasSize(5);
    }

    assertThat(testPerformanceLogger.logEntries())
        .containsExactly(
            PerformanceLogEntry.create("test1", ImmutableMap.of()),
            PerformanceLogEntry.create("test2", ImmutableMap.of("foo", Optional.of("bar"))),
            PerformanceLogEntry.create(
                "test3", ImmutableMap.of("foo1", Optional.of("bar1"), "foo2", Optional.of("bar2"))),
            PerformanceLogEntry.create(
                "test4",
                ImmutableMap.of(
                    "foo1",
                    Optional.of("bar1"),
                    "foo2",
                    Optional.of("bar2"),
                    "foo3",
                    Optional.of("bar3"))),
            PerformanceLogEntry.create(
                "test5",
                ImmutableMap.of(
                    "foo1",
                    Optional.of("bar1"),
                    "foo2",
                    Optional.of("bar2"),
                    "foo3",
                    Optional.of("bar3"),
                    "foo4",
                    Optional.of("bar4"))))
        .inOrder();

    assertThat(LoggingContext.getInstance().isPerformanceLogging()).isFalse();
    assertThat(LoggingContext.getInstance().getPerformanceLogRecords()).isEmpty();
  }

  @Test
  public void traceTimersInsidePerformanceLogContextCreatePerformanceLogNullValuesAllowed() {
    assertThat(LoggingContext.getInstance().isPerformanceLogging()).isFalse();
    assertThat(LoggingContext.getInstance().getPerformanceLogRecords()).isEmpty();

    try (PerformanceLogContext traceContext =
        new PerformanceLogContext(config, performanceLoggers)) {
      assertThat(LoggingContext.getInstance().isPerformanceLogging()).isTrue();

      TraceContext.newTimer("test1").close();
      TraceContext.newTimer("test2", "foo", null).close();
      TraceContext.newTimer("test3", "foo1", null, "foo2", null).close();
      TraceContext.newTimer("test4", "foo1", null, "foo2", null, "foo3", null).close();
      TraceContext.newTimer("test5", "foo1", null, "foo2", null, "foo3", null, "foo4", null)
          .close();

      assertThat(LoggingContext.getInstance().getPerformanceLogRecords()).hasSize(5);
    }

    assertThat(testPerformanceLogger.logEntries())
        .containsExactly(
            PerformanceLogEntry.create("test1", ImmutableMap.of()),
            PerformanceLogEntry.create("test2", ImmutableMap.of("foo", Optional.empty())),
            PerformanceLogEntry.create(
                "test3", ImmutableMap.of("foo1", Optional.empty(), "foo2", Optional.empty())),
            PerformanceLogEntry.create(
                "test4",
                ImmutableMap.of(
                    "foo1", Optional.empty(), "foo2", Optional.empty(), "foo3", Optional.empty())),
            PerformanceLogEntry.create(
                "test5",
                ImmutableMap.of(
                    "foo1",
                    Optional.empty(),
                    "foo2",
                    Optional.empty(),
                    "foo3",
                    Optional.empty(),
                    "foo4",
                    Optional.empty())))
        .inOrder();

    assertThat(LoggingContext.getInstance().isPerformanceLogging()).isFalse();
    assertThat(LoggingContext.getInstance().getPerformanceLogRecords()).isEmpty();
  }

  @Test
  public void traceTimersOutsidePerformanceLogContextDoNotCreatePerformanceLog() {
    assertThat(LoggingContext.getInstance().isPerformanceLogging()).isFalse();
    assertThat(LoggingContext.getInstance().getPerformanceLogRecords()).isEmpty();

    TraceContext.newTimer("test1").close();
    TraceContext.newTimer("test2", "foo", "bar").close();
    TraceContext.newTimer("test3", "foo1", "bar1", "foo2", "bar2").close();
    TraceContext.newTimer("test4", "foo1", "bar1", "foo2", "bar2", "foo3", "bar3").close();
    TraceContext.newTimer("test5", "foo1", "bar1", "foo2", "bar2", "foo3", "bar3", "foo4", "bar4")
        .close();

    assertThat(LoggingContext.getInstance().isPerformanceLogging()).isFalse();
    assertThat(LoggingContext.getInstance().getPerformanceLogRecords()).isEmpty();
    assertThat(testPerformanceLogger.logEntries()).isEmpty();
  }

  @Test
  public void
      traceTimersInsidePerformanceLogContextDoNotCreatePerformanceLogIfNoPerformanceLoggers() {
    // Remove test performance logger so that there are no registered performance loggers.
    performanceLoggerRegistrationHandle.remove();

    assertThat(LoggingContext.getInstance().isPerformanceLogging()).isFalse();
    assertThat(LoggingContext.getInstance().getPerformanceLogRecords()).isEmpty();

    try (PerformanceLogContext traceContext =
        new PerformanceLogContext(config, performanceLoggers)) {
      assertThat(LoggingContext.getInstance().isPerformanceLogging()).isFalse();

      TraceContext.newTimer("test1").close();
      TraceContext.newTimer("test2", "foo", "bar").close();
      TraceContext.newTimer("test3", "foo1", "bar1", "foo2", "bar2").close();
      TraceContext.newTimer("test4", "foo1", "bar1", "foo2", "bar2", "foo3", "bar3").close();
      TraceContext.newTimer("test5", "foo1", "bar1", "foo2", "bar2", "foo3", "bar3", "foo4", "bar4")
          .close();

      assertThat(LoggingContext.getInstance().getPerformanceLogRecords()).isEmpty();
    }

    assertThat(testPerformanceLogger.logEntries()).isEmpty();

    assertThat(LoggingContext.getInstance().isPerformanceLogging()).isFalse();
    assertThat(LoggingContext.getInstance().getPerformanceLogRecords()).isEmpty();
  }

  @Test
  public void timerMetricsInsidePerformanceLogContextCreatePerformanceLog() {
    assertThat(LoggingContext.getInstance().isPerformanceLogging()).isFalse();
    assertThat(LoggingContext.getInstance().getPerformanceLogRecords()).isEmpty();

    try (PerformanceLogContext traceContext =
        new PerformanceLogContext(config, performanceLoggers)) {
      assertThat(LoggingContext.getInstance().isPerformanceLogging()).isTrue();

      Timer0 timer0 =
          metricMaker.newTimer("test1/latency", new Description("Latency metric for testing"));
      timer0.start().close();

      Timer1<String> timer1 =
          metricMaker.newTimer(
              "test2/latency",
              new Description("Latency metric for testing"),
              Field.ofString().name("foo").build());
      timer1.start("value1").close();

      Timer2<String, String> timer2 =
          metricMaker.newTimer(
              "test3/latency",
              new Description("Latency metric for testing"),
              Field.ofString().name("foo").build(),
              Field.ofString().name("bar").build());
      timer2.start("value1", "value2").close();

      Timer3<String, String, String> timer3 =
          metricMaker.newTimer(
              "test4/latency",
              new Description("Latency metric for testing"),
              Field.ofString().name("foo").build(),
              Field.ofString().name("bar").build(),
              Field.ofString().name("baz").build());
      timer3.start("value1", "value2", "value3").close();

      assertThat(LoggingContext.getInstance().getPerformanceLogRecords()).hasSize(4);
    }

    assertThat(testPerformanceLogger.logEntries())
        .containsExactly(
            PerformanceLogEntry.create("test1/latency", ImmutableMap.of()),
            PerformanceLogEntry.create(
                "test2/latency", ImmutableMap.of("foo", Optional.of("value1"))),
            PerformanceLogEntry.create(
                "test3/latency",
                ImmutableMap.of("foo", Optional.of("value1"), "bar", Optional.of("value2"))),
            PerformanceLogEntry.create(
                "test4/latency",
                ImmutableMap.of(
                    "foo",
                    Optional.of("value1"),
                    "bar",
                    Optional.of("value2"),
                    "baz",
                    Optional.of("value3"))))
        .inOrder();

    assertThat(LoggingContext.getInstance().isPerformanceLogging()).isFalse();
    assertThat(LoggingContext.getInstance().getPerformanceLogRecords()).isEmpty();
  }

  @Test
  public void timerMetricsInsidePerformanceLogContextCreatePerformanceLogNullValuesAllowed() {
    assertThat(LoggingContext.getInstance().isPerformanceLogging()).isFalse();
    assertThat(LoggingContext.getInstance().getPerformanceLogRecords()).isEmpty();

    try (PerformanceLogContext traceContext =
        new PerformanceLogContext(config, performanceLoggers)) {
      assertThat(LoggingContext.getInstance().isPerformanceLogging()).isTrue();

      Timer1<String> timer1 =
          metricMaker.newTimer(
              "test1/latency",
              new Description("Latency metric for testing"),
              Field.ofString().name("foo").build());
      timer1.start(null).close();

      Timer2<String, String> timer2 =
          metricMaker.newTimer(
              "test2/latency",
              new Description("Latency metric for testing"),
              Field.ofString().name("foo").build(),
              Field.ofString().name("bar").build());
      timer2.start(null, null).close();

      Timer3<String, String, String> timer3 =
          metricMaker.newTimer(
              "test3/latency",
              new Description("Latency metric for testing"),
              Field.ofString().name("foo").build(),
              Field.ofString().name("bar").build(),
              Field.ofString().name("baz").build());
      timer3.start(null, null, null).close();

      assertThat(LoggingContext.getInstance().getPerformanceLogRecords()).hasSize(3);
    }

    assertThat(testPerformanceLogger.logEntries())
        .containsExactly(
            PerformanceLogEntry.create("test1/latency", ImmutableMap.of("foo", Optional.empty())),
            PerformanceLogEntry.create(
                "test2/latency", ImmutableMap.of("foo", Optional.empty(), "bar", Optional.empty())),
            PerformanceLogEntry.create(
                "test3/latency",
                ImmutableMap.of(
                    "foo", Optional.empty(), "bar", Optional.empty(), "baz", Optional.empty())))
        .inOrder();

    assertThat(LoggingContext.getInstance().isPerformanceLogging()).isFalse();
    assertThat(LoggingContext.getInstance().getPerformanceLogRecords()).isEmpty();
  }

  @Test
  public void timerMetricsOutsidePerformanceLogContextDoNotCreatePerformanceLog() {
    assertThat(LoggingContext.getInstance().isPerformanceLogging()).isFalse();
    assertThat(LoggingContext.getInstance().getPerformanceLogRecords()).isEmpty();

    Timer0 timer0 =
        metricMaker.newTimer("test1/latency", new Description("Latency metric for testing"));
    timer0.start().close();

    Timer1<String> timer1 =
        metricMaker.newTimer(
            "test2/latency",
            new Description("Latency metric for testing"),
            Field.ofString().name("foo").build());
    timer1.start("value1").close();

    Timer2<String, String> timer2 =
        metricMaker.newTimer(
            "test3/latency",
            new Description("Latency metric for testing"),
            Field.ofString().name("foo").build(),
            Field.ofString().name("bar").build());
    timer2.start("value1", "value2").close();

    Timer3<String, String, String> timer3 =
        metricMaker.newTimer(
            "test4/latency",
            new Description("Latency metric for testing"),
            Field.ofString().name("foo").build(),
            Field.ofString().name("bar").build(),
            Field.ofString().name("baz").build());
    timer3.start("value1", "value2", "value3").close();

    assertThat(LoggingContext.getInstance().isPerformanceLogging()).isFalse();
    assertThat(LoggingContext.getInstance().getPerformanceLogRecords()).isEmpty();
    assertThat(testPerformanceLogger.logEntries()).isEmpty();
  }

  @Test
  public void
      timerMetricssInsidePerformanceLogContextDoNotCreatePerformanceLogIfNoPerformanceLoggers() {
    // Remove test performance logger so that there are no registered performance loggers.
    performanceLoggerRegistrationHandle.remove();

    assertThat(LoggingContext.getInstance().isPerformanceLogging()).isFalse();
    assertThat(LoggingContext.getInstance().getPerformanceLogRecords()).isEmpty();

    try (PerformanceLogContext traceContext =
        new PerformanceLogContext(config, performanceLoggers)) {
      assertThat(LoggingContext.getInstance().isPerformanceLogging()).isFalse();

      Timer0 timer0 =
          metricMaker.newTimer("test1/latency", new Description("Latency metric for testing"));
      timer0.start().close();

      Timer1<String> timer1 =
          metricMaker.newTimer(
              "test2/latency",
              new Description("Latency metric for testing"),
              Field.ofString().name("foo").build());
      timer1.start("value1").close();

      Timer2<String, String> timer2 =
          metricMaker.newTimer(
              "test3/latency",
              new Description("Latency metric for testing"),
              Field.ofString().name("foo").build(),
              Field.ofString().name("bar").build());
      timer2.start("value1", "value2").close();

      Timer3<String, String, String> timer3 =
          metricMaker.newTimer(
              "test4/latency",
              new Description("Latency metric for testing"),
              Field.ofString().name("foo").build(),
              Field.ofString().name("bar").build(),
              Field.ofString().name("baz").build());
      timer3.start("value1", "value2", "value3").close();

      assertThat(LoggingContext.getInstance().getPerformanceLogRecords()).isEmpty();
    }

    assertThat(testPerformanceLogger.logEntries()).isEmpty();

    assertThat(LoggingContext.getInstance().isPerformanceLogging()).isFalse();
    assertThat(LoggingContext.getInstance().getPerformanceLogRecords()).isEmpty();
  }

  @Test
  public void nestingPerformanceLogContextsIsPossible() {
    assertThat(LoggingContext.getInstance().isPerformanceLogging()).isFalse();
    assertThat(LoggingContext.getInstance().getPerformanceLogRecords()).isEmpty();

    try (PerformanceLogContext traceContext1 =
        new PerformanceLogContext(config, performanceLoggers)) {
      assertThat(LoggingContext.getInstance().isPerformanceLogging()).isTrue();

      TraceContext.newTimer("test1").close();

      assertThat(LoggingContext.getInstance().getPerformanceLogRecords()).hasSize(1);

      try (PerformanceLogContext traceContext2 =
          new PerformanceLogContext(config, performanceLoggers)) {
        assertThat(LoggingContext.getInstance().getPerformanceLogRecords()).isEmpty();
        assertThat(LoggingContext.getInstance().isPerformanceLogging()).isTrue();

        TraceContext.newTimer("test2").close();
        TraceContext.newTimer("test3").close();

        assertThat(LoggingContext.getInstance().getPerformanceLogRecords()).hasSize(2);
      }

      assertThat(LoggingContext.getInstance().isPerformanceLogging()).isTrue();
      assertThat(LoggingContext.getInstance().getPerformanceLogRecords()).hasSize(1);
    }
    assertThat(LoggingContext.getInstance().isPerformanceLogging()).isFalse();
    assertThat(LoggingContext.getInstance().getPerformanceLogRecords()).isEmpty();
  }

  private static class TestPerformanceLogger implements PerformanceLogger {
    private List<PerformanceLogEntry> logEntries = new ArrayList<>();

    @Override
    public void log(String operation, long durationMs, Map<String, Optional<Object>> metaData) {
      logEntries.add(PerformanceLogEntry.create(operation, metaData));
    }

    ImmutableList<PerformanceLogEntry> logEntries() {
      return ImmutableList.copyOf(logEntries);
    }
  }

  @AutoValue
  abstract static class PerformanceLogEntry {
    static PerformanceLogEntry create(String operation, Map<String, Optional<Object>> metaData) {
      return new AutoValue_PerformanceLogContextTest_PerformanceLogEntry(
          operation, ImmutableMap.copyOf(metaData));
    }

    abstract String operation();

    abstract ImmutableMap<String, Object> metaData();
  }
}
