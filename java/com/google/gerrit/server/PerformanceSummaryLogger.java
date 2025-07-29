// Copyright (C) 2025 The Android Open Source Project
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

package com.google.gerrit.server;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Comparator.comparing;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.server.logging.Metadata;
import com.google.gerrit.server.logging.PerformanceLogger;
import com.google.gerrit.server.logging.TraceContext;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Performance logger that logs a performance summary with the most expensive operations at the end
 * of a request, but only if the request is being traced.
 */
public class PerformanceSummaryLogger implements PerformanceLogger {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Max number of operations for which the latency should be logged. */
  private static final int MAX_OPERATIONS = 20;

  private final Map<String, PerformanceInfo> perRequestPerformanceInfo = new HashMap<>();

  @Override
  public void logNanos(String operation, long durationNanos, Instant endTime, Metadata metadata) {
    if (!TraceContext.isTracing()) {
      return;
    }

    String pluginTag = TraceContext.getPluginTag().orElse("gerrit");
    perRequestPerformanceInfo.compute(
        "gerrit".equals(pluginTag) ? operation : pluginTag + "~" + operation,
        (operationName, performanceInfo) ->
            performanceInfo == null
                ? new PerformanceInfo(operationName, durationNanos)
                : performanceInfo.add(durationNanos));
  }

  @Override
  public void done() {
    if (!TraceContext.isTracing()) {
      return;
    }

    ImmutableList<PerformanceInfo> performanceInfosWithLongestTotalDuration =
        perRequestPerformanceInfo.values().stream()
            .filter(performanceLogInfo -> performanceLogInfo.totalDurationMillis() > 0)
            .sorted(comparing(PerformanceInfo::totalDurationNanos).reversed())
            .limit(MAX_OPERATIONS)
            .collect(toImmutableList());

    if (!performanceInfosWithLongestTotalDuration.isEmpty()) {
      logger.atFine().log(
          "[Performance Summery] Operations with the highest latency (max %s):\n%s",
          MAX_OPERATIONS, Joiner.on('\n').join(performanceInfosWithLongestTotalDuration));
    }
  }

  static class PerformanceInfo {
    private final String operationName;

    private int count;
    private long totalDurationNanos;

    PerformanceInfo(String operationName, long durationNanos) {
      this.operationName = operationName;
      this.count = 1;
      this.totalDurationNanos = durationNanos;
    }

    PerformanceInfo add(long durationNanos) {
      this.count++;
      this.totalDurationNanos += durationNanos;
      return this;
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
