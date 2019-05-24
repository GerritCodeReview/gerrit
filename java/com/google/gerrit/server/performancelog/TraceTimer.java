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

package com.google.gerrit.server.performancelog;

import static java.util.Objects.requireNonNull;

import com.google.common.base.Stopwatch;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.logging.TraceContext;
import com.google.gerrit.server.plugincontext.PluginContext;
import com.google.gerrit.server.plugincontext.PluginContext.ExtensionImplConsumer;
import com.google.inject.Inject;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class TraceTimer implements AutoCloseable {
  public static class Factory {
    /**
     * Returns a factory that creates {@link TraceTimer}s that only log the execution times in the
     * normal server log (on fine level). Implementations of {@link PerformanceLogger} will not be
     * invoked.
     *
     * <p>Use this only in code that doesn't allow to inject {@link TraceTimer.Factory}.
     *
     * @return the factory
     */
    public static Factory createWithoutPerformanceLogger() {
      return new Factory(DynamicSet.emptySet());
    }

    // Do not use PluginSetContext. PluginSetContext traces the plugin latency with a timer metric
    // which would result in a performance log and we don't want to log the performance of writing
    // a performance log in the performance log.
    private final DynamicSet<PerformanceLogger> performanceLogger;

    @Inject
    Factory(DynamicSet<PerformanceLogger> performanceLogger) {
      this.performanceLogger = performanceLogger;
    }

    /**
     * Opens a new timer that logs the time for an operation if request tracing is enabled.
     *
     * @param operation the name of operation the is being performed
     * @return the trace timer
     */
    public TraceTimer newTimer(String operation) {
      return new TraceTimer(performanceLogger, requireNonNull(operation, "operation is required"));
    }

    /**
     * Opens a new timer that logs the time for an operation if request tracing is enabled.
     *
     * @param operation the name of operation the is being performed
     * @param key meta data key
     * @param value meta data value
     * @return the trace timer
     */
    public TraceTimer newTimer(String operation, String key, @Nullable Object value) {
      return new TraceTimer(
          performanceLogger,
          requireNonNull(operation, "operation is required"),
          requireNonNull(key, "key is required"),
          value);
    }

    /**
     * Opens a new timer that logs the time for an operation if request tracing is enabled.
     *
     * @param operation the name of operation the is being performed
     * @param key1 first meta data key
     * @param value1 first meta data value
     * @param key2 second meta data key
     * @param value2 second meta data value
     * @return the trace timer
     */
    public TraceTimer newTimer(
        String operation,
        String key1,
        @Nullable Object value1,
        String key2,
        @Nullable Object value2) {
      return new TraceTimer(
          performanceLogger,
          requireNonNull(operation, "operation is required"),
          requireNonNull(key1, "key1 is required"),
          value1,
          requireNonNull(key2, "key2 is required"),
          value2);
    }

    /**
     * Opens a new timer that logs the time for an operation if request tracing is enabled.
     *
     * @param operation the name of operation the is being performed
     * @param key1 first meta data key
     * @param value1 first meta data value
     * @param key2 second meta data key
     * @param value2 second meta data value
     * @param key3 third meta data key
     * @param value3 third meta data value
     * @return the trace timer
     */
    public TraceTimer newTimer(
        String operation,
        String key1,
        @Nullable Object value1,
        String key2,
        @Nullable Object value2,
        String key3,
        @Nullable Object value3) {
      return new TraceTimer(
          performanceLogger,
          requireNonNull(operation, "operation is required"),
          requireNonNull(key1, "key1 is required"),
          value1,
          requireNonNull(key2, "key2 is required"),
          value2,
          requireNonNull(key3, "key3 is required"),
          value3);
    }

    /**
     * Opens a new timer that logs the time for an operation if request tracing is enabled.
     *
     * @param operation the name of operation the is being performed
     * @param key1 first meta data key
     * @param value1 first meta data value
     * @param key2 second meta data key
     * @param value2 second meta data value
     * @param key3 third meta data key
     * @param value3 third meta data value
     * @param key4 fourth meta data key
     * @param value4 fourth meta data value
     * @return the trace timer
     */
    public TraceTimer newTimer(
        String operation,
        String key1,
        @Nullable Object value1,
        String key2,
        @Nullable Object value2,
        String key3,
        @Nullable Object value3,
        String key4,
        @Nullable Object value4) {
      return new TraceTimer(
          performanceLogger,
          requireNonNull(operation, "operation is required"),
          requireNonNull(key1, "key1 is required"),
          value1,
          requireNonNull(key2, "key2 is required"),
          value2,
          requireNonNull(key3, "key3 is required"),
          value3,
          requireNonNull(key4, "key4 is required"),
          value4);
    }
  }

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Consumer<Long> logFn;
  private final Stopwatch stopwatch;

  private TraceTimer(DynamicSet<PerformanceLogger> performanceLogger, String operation) {
    this(
        elapsedMs -> {
          runEach(performanceLogger, p -> p.log(operation, elapsedMs));
          logger.atFine().log("%s (%d ms)", operation, elapsedMs);
        });
  }

  private TraceTimer(
      DynamicSet<PerformanceLogger> performanceLogger,
      String operation,
      String key,
      @Nullable Object value) {
    this(
        elapsedMs -> {
          runEach(performanceLogger, p -> p.log(operation, elapsedMs, key, value));
          logger.atFine().log("%s (%s=%s) (%d ms)", operation, key, value, elapsedMs);
        });
  }

  private TraceTimer(
      DynamicSet<PerformanceLogger> performanceLogger,
      String operation,
      String key1,
      @Nullable Object value1,
      String key2,
      @Nullable Object value2) {
    this(
        elapsedMs -> {
          runEach(performanceLogger, p -> p.log(operation, elapsedMs, key1, value1, key2, value2));
          logger.atFine().log(
              "%s (%s=%s, %s=%s) (%d ms)", operation, key1, value1, key2, value2, elapsedMs);
        });
  }

  private TraceTimer(
      DynamicSet<PerformanceLogger> performanceLogger,
      String operation,
      String key1,
      @Nullable Object value1,
      String key2,
      @Nullable Object value2,
      String key3,
      @Nullable Object value3) {
    this(
        elapsedMs -> {
          runEach(
              performanceLogger,
              p -> p.log(operation, elapsedMs, key1, value1, key2, value2, key3, value3));
          logger.atFine().log(
              "%s (%s=%s, %s=%s, %s=%s) (%d ms)",
              operation, key1, value1, key2, value2, key3, value3, elapsedMs);
        });
  }

  private TraceTimer(
      DynamicSet<PerformanceLogger> performanceLogger,
      String operation,
      String key1,
      @Nullable Object value1,
      String key2,
      @Nullable Object value2,
      String key3,
      @Nullable Object value3,
      String key4,
      @Nullable Object value4) {
    this(
        elapsedMs -> {
          runEach(
              performanceLogger,
              p ->
                  p.log(
                      operation, elapsedMs, key1, value1, key2, value2, key3, value3, key4,
                      value4));
          logger.atFine().log(
              "%s (%s=%s, %s=%s, %s=%s, %s=%s) (%d ms)",
              operation, key1, value1, key2, value2, key3, value3, key4, value4, elapsedMs);
        });
  }

  private TraceTimer(Consumer<Long> logFn) {
    this.logFn = logFn;
    this.stopwatch = Stopwatch.createStarted();
  }

  @Override
  public void close() {
    stopwatch.stop();
    logFn.accept(stopwatch.elapsed(TimeUnit.MILLISECONDS));
  }

  private static <T> void runEach(
      DynamicSet<T> dynamicSet, ExtensionImplConsumer<T> extensionImplConsumer) {
    dynamicSet
        .entries()
        .forEach(
            p -> {
              try (TraceContext traceContext = PluginContext.newTrace(p)) {
                extensionImplConsumer.run(p.get());
              } catch (Throwable e) {
                logger.atWarning().withCause(e).log(
                    "Failure in %s of plugin %s", p.get().getClass(), p.getPluginName());
              }
            });
  }
}
