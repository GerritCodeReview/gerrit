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

package com.google.gerrit.server.permissions.performancelog;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.server.performancelog.PerformanceLogger;
import com.google.gerrit.server.performancelog.TraceTimer;
import com.google.gerrit.testing.InMemoryModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TraceTimerTest {
  @Inject private TraceTimer.Factory traceTimerFactory;
  @Inject private DynamicSet<PerformanceLogger> performanceLogger;

  private TestPerformanceLogger testPerformanceLogger;
  private RegistrationHandle performanceLoggerRegistrationHandle;

  @Before
  public void setup() {
    Injector injector = Guice.createInjector(new InMemoryModule());
    injector.injectMembers(this);

    testPerformanceLogger = new TestPerformanceLogger();
    performanceLoggerRegistrationHandle = performanceLogger.add("gerrit", testPerformanceLogger);
  }

  @After
  public void cleanup() {
    performanceLoggerRegistrationHandle.remove();
  }

  @Test
  public void operationCannotBeNull() throws Exception {
    assertThrows(NullPointerException.class, () -> traceTimerFactory.newTimer(null));
    assertThrows(NullPointerException.class, () -> traceTimerFactory.newTimer(null, "foo", "bar"));
    assertThrows(
        NullPointerException.class,
        () -> traceTimerFactory.newTimer(null, "foo1", "bar1", "foo2", "bar2"));
    assertThrows(
        NullPointerException.class,
        () -> traceTimerFactory.newTimer(null, "foo1", "bar1", "foo2", "bar2", "foo3", "bar3"));
    assertThrows(
        NullPointerException.class,
        () ->
            traceTimerFactory.newTimer(
                null, "foo1", "bar1", "foo2", "bar2", "foo3", "bar3", "foo4", "bar4"));
  }

  @Test
  public void keysCannotBeNull() throws Exception {
    assertThrows(NullPointerException.class, () -> traceTimerFactory.newTimer("test", null, "bar"));
    assertThrows(
        NullPointerException.class,
        () -> traceTimerFactory.newTimer("test", null, "bar1", "foo2", "bar2"));
    assertThrows(
        NullPointerException.class,
        () -> traceTimerFactory.newTimer("test", "foo1", "bar1", null, "bar2"));
    assertThrows(
        NullPointerException.class,
        () -> traceTimerFactory.newTimer("test", null, "bar1", "foo2", "bar2", "foo3", "bar3"));
    assertThrows(
        NullPointerException.class,
        () -> traceTimerFactory.newTimer("test", "foo1", "bar1", null, "bar2", "foo3", "bar3"));
    assertThrows(
        NullPointerException.class,
        () -> traceTimerFactory.newTimer("test", "foo1", "bar1", "foo2", "bar2", null, "bar3"));
    assertThrows(
        NullPointerException.class,
        () ->
            traceTimerFactory.newTimer(
                "test", null, "bar1", "foo2", "bar2", "foo3", "bar3", "foo4", "bar4"));
    assertThrows(
        NullPointerException.class,
        () ->
            traceTimerFactory.newTimer(
                "test", "foo1", "bar1", null, "bar2", "foo3", "bar3", "foo4", "bar4"));
    assertThrows(
        NullPointerException.class,
        () ->
            traceTimerFactory.newTimer(
                "test", "foo1", "bar1", "foo2", "bar2", null, "bar3", "foo4", "bar4"));
    assertThrows(
        NullPointerException.class,
        () ->
            traceTimerFactory.newTimer(
                "test", "foo1", "bar1", "foo2", "bar2", "foo3", "bar3", null, "bar4"));
  }

  @Test
  public void traceTimerInvokesPerformanceLogger() throws Exception {
    traceTimerFactory.newTimer("test1").close();
    traceTimerFactory.newTimer("test2", "foo", "bar").close();
    traceTimerFactory.newTimer("test3", "foo1", "bar1", "foo2", "bar2").close();
    traceTimerFactory.newTimer("test4", "foo1", "bar1", "foo2", "bar2", "foo3", "bar3").close();
    traceTimerFactory
        .newTimer("test5", "foo1", "bar1", "foo2", "bar2", "foo3", "bar3", "foo4", "bar4")
        .close();

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
  }

  @Test
  public void traceTimerInvokesPerformanceLoggerNullValuesAllowed() throws Exception {
    traceTimerFactory.newTimer("test1").close();
    traceTimerFactory.newTimer("test2", "foo", null).close();
    traceTimerFactory.newTimer("test3", "foo1", null, "foo2", null).close();
    traceTimerFactory.newTimer("test4", "foo1", null, "foo2", null, "foo3", null).close();
    traceTimerFactory
        .newTimer("test5", "foo1", null, "foo2", null, "foo3", null, "foo4", null)
        .close();

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
  }

  @Test
  public void traceTimerCreatedWithoutPerformanceLoggerDoesNotInvokePerformanceLogger() {
    TraceTimer.Factory.createWithoutPerformanceLogger().newTimer("test1").close();
    TraceTimer.Factory.createWithoutPerformanceLogger().newTimer("test2", "foo", "bar").close();
    TraceTimer.Factory.createWithoutPerformanceLogger()
        .newTimer("test3", "foo1", "bar1", "foo2", "bar2")
        .close();
    TraceTimer.Factory.createWithoutPerformanceLogger()
        .newTimer("test4", "foo1", "bar1", "foo2", "bar2", "foo3", "bar3")
        .close();
    TraceTimer.Factory.createWithoutPerformanceLogger()
        .newTimer("test5", "foo1", "bar1", "foo2", "bar2", "foo3", "bar3", "foo4", "bar4")
        .close();

    assertThat(testPerformanceLogger.logEntries()).isEmpty();
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
      return new AutoValue_TraceTimerTest_PerformanceLogEntry(
          operation, ImmutableMap.copyOf(metaData));
    }

    abstract String operation();

    abstract ImmutableMap<String, Object> metaData();
  }
}
