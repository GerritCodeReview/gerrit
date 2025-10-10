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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.flogger.LazyArgs.lazy;
import static java.util.Comparator.comparing;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.registration.Extension;
import com.google.gerrit.server.cancellation.PerformanceSummaryProvider;
import com.google.gerrit.server.util.time.TimeUtil;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.eclipse.jgit.lib.Config;

/**
 * Context for capturing performance log records. When the context is closed the performance log
 * records are handed over to the registered {@link PerformanceLogger}s.
 *
 * <p>Capturing performance log records is disabled if there are no {@link PerformanceLogger}
 * registered (in this case the captured performance log records would never be used).
 *
 * <p>It's important to enable capturing of performance log records in a context that ensures to
 * consume the captured performance log records. Otherwise captured performance log records might
 * leak into other requests that are executed by the same thread (if a thread pool is used to
 * process requests).
 */
public class PerformanceLogContext implements AutoCloseable, PerformanceSummaryProvider {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  // Keep in sync with PluginMetrics.PLUGIN_LATENCY_NAME.
  private static final String PLUGIN_LATENCY_NAME = "plugin/latency";

  /** Default for maximum number of operations for which the latency should be logged. */
  private static final int DEFAULT_MAX_OPERATIONS_TO_LOG = 25;

  // Do not use PluginSetContext. PluginSetContext traces the plugin latency with a timer metric
  // which would result in a performance log and we don't want to log the performance of writing
  // a performance log in the performance log (endless loop).
  private final DynamicSet<PerformanceLogger> performanceLoggers;

  private final boolean oldPerformanceLogging;
  private final ImmutableList<PerformanceLogRecord> oldPerformanceLogRecords;

  /** Maximum number of operations for which the latency should be logged. */
  private final int maxOperationsToLog;

  /** Threshold in nanos from which on a request is considered as "slow" if exceeded. */
  private final long slowRequestThresholdNanos;

  private long startNanos;

  public PerformanceLogContext(
      Config gerritConfig, DynamicSet<PerformanceLogger> performanceLoggers) {
    this.performanceLoggers = performanceLoggers;

    // Just in case remember the old state and reset performance log entries.
    this.oldPerformanceLogging = LoggingContext.getInstance().isPerformanceLogging();
    this.oldPerformanceLogRecords = LoggingContext.getInstance().getPerformanceLogRecords();
    LoggingContext.getInstance().clearPerformanceLogEntries();

    LoggingContext.getInstance()
        .performanceLogging(gerritConfig.getBoolean("tracing", "performanceLogging", false));

    this.maxOperationsToLog =
        gerritConfig.getInt("performance", "maxOperationsToLog", DEFAULT_MAX_OPERATIONS_TO_LOG);

    this.slowRequestThresholdNanos =
        gerritConfig.getTimeUnit(
            "performance",
            /* subsection= */ null,
            "slowRequestThreshold",
            SECONDS.toNanos(30),
            NANOSECONDS);
    this.startNanos = TimeUtil.nowNanos();
  }

  @Override
  public void close() {
    if (LoggingContext.getInstance().isPerformanceLogging()) {
      runEach(performanceLoggers, LoggingContext.getInstance().getPerformanceLogRecords());
    }

    long elapsedNanos = TimeUtil.nowNanos() - startNanos;
    logger.at(elapsedNanos > slowRequestThresholdNanos ? Level.WARNING : Level.FINE).log(
        "%s",
        lazy(
            () ->
                getPerformanceSummary()
                    .map(
                        performanceSummary ->
                            String.format(
                                "%s\n\n%s",
                                elapsedNanos > slowRequestThresholdNanos
                                    ? String.format(
                                        "Performance Summary for slow request (request took longer"
                                            + " than %ss)",
                                        NANOSECONDS.toSeconds(slowRequestThresholdNanos))
                                    : "[Performance Summary]",
                                performanceSummary))
                    .orElse("No performance summary available")));

    // Restore old state. Required to support nesting of PerformanceLogContext's.
    LoggingContext.getInstance().performanceLogging(oldPerformanceLogging);
    LoggingContext.getInstance().setPerformanceLogRecords(oldPerformanceLogRecords);
  }

  @Override
  public Optional<String> getPerformanceSummary() {
    ImmutableList<PerformanceLogRecord> performanceLogRecords =
        LoggingContext.getInstance().getPerformanceLogRecords();
    if (maxOperationsToLog <= 0 || performanceLogRecords.isEmpty()) {
      return Optional.empty();
    }

    Map<String, PerformanceInfo> perRequestPerformanceInfo = new HashMap<>();
    for (PerformanceLogRecord performanceLogRecord : performanceLogRecords) {
      String pluginClass =
          PLUGIN_LATENCY_NAME.equals(performanceLogRecord.operation())
              ? performanceLogRecord
                  .metadata()
                  .map(Metadata::className)
                  .map(clazz -> " (" + clazz + ")")
                  .orElse("")
              : "";
      PerformanceInfo info =
          perRequestPerformanceInfo.computeIfAbsent(
              performanceLogRecord.operation() + pluginClass,
              operationName -> new PerformanceInfo(operationName));
      info.add(performanceLogRecord.durationNanos());
    }

    ImmutableList<PerformanceInfo> performanceInfosWithLongestTotalDuration =
        perRequestPerformanceInfo.values().stream()
            .filter(performanceLogInfo -> performanceLogInfo.totalDurationMillis() > 0)
            .sorted(comparing(PerformanceInfo::totalDurationNanos).reversed())
            .limit(maxOperationsToLog)
            .collect(toImmutableList());

    ImmutableList<PerformanceInfo> performanceInfosForOperationsThatHaveBeenCalledMostOften =
        perRequestPerformanceInfo.values().stream()
            .filter(performanceLogInfo -> performanceLogInfo.count() > 1)
            .sorted(
                comparing(PerformanceInfo::count)
                    .thenComparing(PerformanceInfo::totalDurationNanos)
                    .reversed())
            .limit(maxOperationsToLog)
            .collect(toImmutableList());

    return Optional.of(
        String.format(
            """
            Operations with the highest latency (max %s):
            %s

            Operations which have been called most often (max %s):
            %s
            """,
            maxOperationsToLog,
            Joiner.on('\n').join(performanceInfosWithLongestTotalDuration),
            maxOperationsToLog,
            Joiner.on('\n').join(performanceInfosForOperationsThatHaveBeenCalledMostOften)));
  }

  /**
   * Invokes all performance loggers.
   *
   * <p>Similar to how {@code com.google.gerrit.server.plugincontext.PluginContext} invokes plugins
   * but without recording metrics for invoking {@link PerformanceLogger}s.
   *
   * @param performanceLoggers the performance loggers that should be invoked
   * @param performanceLogRecords the performance log records that should be handed over to the
   *     performance loggers
   */
  private static void runEach(
      DynamicSet<PerformanceLogger> performanceLoggers,
      ImmutableList<PerformanceLogRecord> performanceLogRecords) {
    performanceLoggers
        .entries()
        .forEach(
            p -> {
              PerformanceLogger performanceLogger = p.get();
              try (TraceContext traceContext = newPluginTrace(p)) {
                performanceLogRecords.forEach(r -> r.writeTo(performanceLogger));
                performanceLogger.done();
              } catch (RuntimeException e) {
                logger.atWarning().withCause(e).log(
                    "Failure in %s of plugin %s", performanceLogger.getClass(), p.getPluginName());
              }
            });
  }

  /**
   * Opens a trace context for a plugin that implements {@link PerformanceLogger}.
   *
   * <p>Basically the same as {@code
   * com.google.gerrit.server.plugincontext.PluginContext#newTrace(Extension<T>)}. We have this
   * method here to avoid a dependency on PluginContext which lives in
   * "//java/com/google/gerrit/server". This package ("//java/com/google/gerrit/server/logging")
   * should have as few dependencies as possible.
   *
   * @param extension performance logger extension
   * @return the trace context
   */
  private static TraceContext newPluginTrace(Extension<PerformanceLogger> extension) {
    return TraceContext.open().addPluginTag(extension.getPluginName());
  }

  static class PerformanceInfo {
    private final String operationName;

    private int count;
    private long totalDurationNanos;

    PerformanceInfo(String operationName) {
      this.operationName = operationName;
      this.count = 0;
      this.totalDurationNanos = 0;
    }

    void add(long durationNanos) {
      this.count++;
      this.totalDurationNanos += durationNanos;
    }

    int count() {
      return count;
    }

    long totalDurationNanos() {
      return totalDurationNanos;
    }

    long totalDurationMillis() {
      return TimeUnit.NANOSECONDS.toMillis(totalDurationNanos);
    }

    @Override
    public String toString() {
      long totalDurationMillis = totalDurationMillis();
      if (count == 1) {
        return String.format("%sx %s (total: %sms)", count, operationName, totalDurationMillis);
      }

      long averageMillis = totalDurationMillis / count;
      return String.format(
          "%sx %s (total: %sms, avg: %sms)",
          count, operationName, totalDurationMillis, averageMillis);
    }
  }
}
