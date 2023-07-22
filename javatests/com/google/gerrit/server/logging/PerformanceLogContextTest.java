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
import java.util.concurrent.CopyOnWriteArrayList;
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

    // Enable performance logging
    config.setBoolean("tracing", null, "performanceLogging", true);
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
      TraceContext.newTimer("test2", Metadata.builder().accountId(1000000).changeId(123).build())
          .close();

      assertThat(LoggingContext.getInstance().getPerformanceLogRecords()).hasSize(2);
    }

    assertThat(testPerformanceLogger.logEntries())
        .containsExactly(
            PerformanceLogEntry.create("test1", Metadata.empty()),
            PerformanceLogEntry.create(
                "test2", Metadata.builder().accountId(1000000).changeId(123).build()))
        .inOrder();

    assertThat(LoggingContext.getInstance().isPerformanceLogging()).isFalse();
    assertThat(LoggingContext.getInstance().getPerformanceLogRecords()).isEmpty();
  }

  @Test
  public void traceTimersOutsidePerformanceLogContextDoNotCreatePerformanceLog() {
    assertThat(LoggingContext.getInstance().isPerformanceLogging()).isFalse();
    assertThat(LoggingContext.getInstance().getPerformanceLogRecords()).isEmpty();

    TraceContext.newTimer("test1").close();
    TraceContext.newTimer("test2", Metadata.builder().accountId(1000000).changeId(123).build())
        .close();

    assertThat(LoggingContext.getInstance().isPerformanceLogging()).isFalse();
    assertThat(LoggingContext.getInstance().getPerformanceLogRecords()).isEmpty();
    assertThat(testPerformanceLogger.logEntries()).isEmpty();
  }

  @Test
  public void
      traceTimersInsidePerformanceLogContextDoNotCreatePerformanceLogIfNoPerformanceLoggers()
          throws Exception {
    // Remove test performance logger so that there are no registered performance loggers.
    removeAllPerformanceLoggers();

    assertThat(LoggingContext.getInstance().isPerformanceLogging()).isFalse();
    assertThat(LoggingContext.getInstance().getPerformanceLogRecords()).isEmpty();

    try (PerformanceLogContext traceContext =
        new PerformanceLogContext(config, performanceLoggers)) {
      assertThat(LoggingContext.getInstance().isPerformanceLogging()).isFalse();

      TraceContext.newTimer("test1").close();
      TraceContext.newTimer("test2", Metadata.builder().accountId(1000000).changeId(123).build())
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

      Timer1<Integer> timer1 =
          metricMaker.newTimer(
              "test2/latency",
              new Description("Latency metric for testing"),
              Field.ofInteger("account", Metadata.Builder::accountId).build());
      timer1.start(1000000).close();

      Timer2<Integer, Integer> timer2 =
          metricMaker.newTimer(
              "test3/latency",
              new Description("Latency metric for testing"),
              Field.ofInteger("account", Metadata.Builder::accountId).build(),
              Field.ofInteger("change", Metadata.Builder::changeId).build());
      timer2.start(1000000, 123).close();

      Timer3<Integer, Integer, String> timer3 =
          metricMaker.newTimer(
              "test4/latency",
              new Description("Latency metric for testing"),
              Field.ofInteger("account", Metadata.Builder::accountId).build(),
              Field.ofInteger("change", Metadata.Builder::changeId).build(),
              Field.ofProjectName("project").build());
      timer3.start(1000000, 123, "foo/bar").close();

      assertThat(LoggingContext.getInstance().getPerformanceLogRecords()).hasSize(4);
    }

    assertThat(testPerformanceLogger.logEntries())
        .containsExactly(
            PerformanceLogEntry.create("test1/latency", Metadata.empty()),
            PerformanceLogEntry.create(
                "test2/latency", Metadata.builder().accountId(1000000).build()),
            PerformanceLogEntry.create(
                "test3/latency", Metadata.builder().accountId(1000000).changeId(123).build()),
            PerformanceLogEntry.create(
                "test4/latency",
                Metadata.builder().accountId(1000000).changeId(123).projectName("foo/bar").build()))
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
              Field.ofProjectName("project").build());
      timer1.start(null).close();

      Timer2<String, String> timer2 =
          metricMaker.newTimer(
              "test2/latency",
              new Description("Latency metric for testing"),
              Field.ofProjectName("project").build(),
              Field.ofString("branch", Metadata.Builder::branchName).build());
      timer2.start(null, null).close();

      Timer3<String, String, String> timer3 =
          metricMaker.newTimer(
              "test3/latency",
              new Description("Latency metric for testing"),
              Field.ofProjectName("project").build(),
              Field.ofString("branch", Metadata.Builder::branchName).build(),
              Field.ofString("revision", Metadata.Builder::revision).build());
      timer3.start(null, null, null).close();

      assertThat(LoggingContext.getInstance().getPerformanceLogRecords()).hasSize(3);
    }

    assertThat(testPerformanceLogger.logEntries())
        .containsExactly(
            PerformanceLogEntry.create("test1/latency", Metadata.empty()),
            PerformanceLogEntry.create("test2/latency", Metadata.empty()),
            PerformanceLogEntry.create("test3/latency", Metadata.empty()))
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

    Timer1<Integer> timer1 =
        metricMaker.newTimer(
            "test2/latency",
            new Description("Latency metric for testing"),
            Field.ofInteger("account", Metadata.Builder::accountId).build());
    timer1.start(1000000).close();

    Timer2<Integer, Integer> timer2 =
        metricMaker.newTimer(
            "test3/latency",
            new Description("Latency metric for testing"),
            Field.ofInteger("account", Metadata.Builder::accountId).build(),
            Field.ofInteger("change", Metadata.Builder::changeId).build());
    timer2.start(1000000, 123).close();

    Timer3<Integer, Integer, String> timer3 =
        metricMaker.newTimer(
            "test4/latency",
            new Description("Latency metric for testing"),
            Field.ofInteger("account", Metadata.Builder::accountId).build(),
            Field.ofInteger("change", Metadata.Builder::changeId).build(),
            Field.ofProjectName("project").build());
    timer3.start(1000000, 123, "value3").close();

    assertThat(LoggingContext.getInstance().isPerformanceLogging()).isFalse();
    assertThat(LoggingContext.getInstance().getPerformanceLogRecords()).isEmpty();
    assertThat(testPerformanceLogger.logEntries()).isEmpty();
  }

  @Test
  public void
      timerMetricssInsidePerformanceLogContextDoNotCreatePerformanceLogIfNoPerformanceLoggers()
          throws Exception {
    // Remove all performance loggers so that there are no registered performance loggers.
    removeAllPerformanceLoggers();

    assertThat(LoggingContext.getInstance().isPerformanceLogging()).isFalse();
    assertThat(LoggingContext.getInstance().getPerformanceLogRecords()).isEmpty();

    try (PerformanceLogContext traceContext =
        new PerformanceLogContext(config, performanceLoggers)) {
      assertThat(LoggingContext.getInstance().isPerformanceLogging()).isFalse();

      Timer0 timer0 =
          metricMaker.newTimer("test1/latency", new Description("Latency metric for testing"));
      timer0.start().close();

      Timer1<Integer> timer1 =
          metricMaker.newTimer(
              "test2/latency",
              new Description("Latency metric for testing"),
              Field.ofInteger("accoutn", Metadata.Builder::accountId).build());
      timer1.start(1000000).close();

      Timer2<Integer, Integer> timer2 =
          metricMaker.newTimer(
              "test3/latency",
              new Description("Latency metric for testing"),
              Field.ofInteger("account", Metadata.Builder::accountId).build(),
              Field.ofInteger("change", Metadata.Builder::changeId).build());
      timer2.start(1000000, 123).close();

      Timer3<Integer, Integer, String> timer3 =
          metricMaker.newTimer(
              "test4/latency",
              new Description("Latency metric for testing"),
              Field.ofInteger("account", Metadata.Builder::accountId).build(),
              Field.ofInteger("change", Metadata.Builder::changeId).build(),
              Field.ofProjectName("project").build());
      timer3.start(1000000, 123, "foo/bar").close();

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
    private ImmutableList.Builder<PerformanceLogEntry> logEntries = ImmutableList.builder();

    @Override
    public void log(String operation, long durationMs, Metadata metadata) {
      logEntries.add(PerformanceLogEntry.create(operation, metadata));
    }

    ImmutableList<PerformanceLogEntry> logEntries() {
      return logEntries.build();
    }
  }

  private void removeAllPerformanceLoggers() throws Exception {
    java.lang.reflect.Field itemsField = DynamicSet.class.getDeclaredField("items");
    itemsField.setAccessible(true);
    ((CopyOnWriteArrayList<?>) itemsField.get(performanceLoggers)).clear();
  }

  @AutoValue
  abstract static class PerformanceLogEntry {
    static PerformanceLogEntry create(String operation, Metadata metadata) {
      return new AutoValue_PerformanceLogContextTest_PerformanceLogEntry(operation, metadata);
    }

    abstract String operation();

    abstract Metadata metadata();
  }
}
