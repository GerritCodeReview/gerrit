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

package com.google.gerrit.server;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.metrics.Counter3;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.metrics.Timer3;
import com.google.gerrit.server.logging.Metadata;
import com.google.gerrit.server.logging.PerformanceLogger;
import com.google.gerrit.server.logging.TraceContext;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.concurrent.TimeUnit;

/** Performance logger that records the execution times as a metric. */
@Singleton
public class PerformanceMetrics implements PerformanceLogger {
  private static final String OPERATION_LATENCY_METRIC_NAME = "performance/operations";
  private static final String OPERATION_COUNT_METRIC_NAME = "performance/operations_count";

  public final Timer3<String, String, String> operationsLatency;
  public final Counter3<String, String, String> operationsCounter;

  @Inject
  PerformanceMetrics(MetricMaker metricMaker) {
    Field<String> operationNameField =
        Field.ofString(
                "operation_name",
                (metadataBuilder, fieldValue) -> metadataBuilder.operationName(fieldValue))
            .description("The operation that was performed.")
            .build();
    Field<String> requestField =
        Field.ofString("request", (metadataBuilder, fieldValue) -> {})
            .description(
                "The request for which the operation was performed"
                    + " (format = '<request-type> <redacted-request-uri>').")
            .build();
    Field<String> pluginField =
        Field.ofString(
                "plugin", (metadataBuilder, fieldValue) -> metadataBuilder.pluginName(fieldValue))
            .description("The name of the plugin that performed the operation.")
            .build();

    this.operationsLatency =
        metricMaker.newTimer(
            OPERATION_LATENCY_METRIC_NAME,
            new Description("Latency of performing operations")
                .setCumulative()
                .setUnit(Description.Units.MILLISECONDS),
            operationNameField,
            requestField,
            pluginField);
    this.operationsCounter =
        metricMaker.newCounter(
            OPERATION_COUNT_METRIC_NAME,
            new Description("Number of performed operations").setRate(),
            operationNameField,
            requestField,
            pluginField);
  }

  @Override
  public void log(String operation, long durationMs) {
    log(operation, durationMs, /* metadata= */ null);
  }

  @Override
  public void log(String operation, long durationMs, @Nullable Metadata metadata) {
    if (OPERATION_LATENCY_METRIC_NAME.equals(operation)) {
      // Recording the timer metric below triggers writing a performance log entry. If we are called
      // for this performance log entry we must abort to avoid an endless loop.
      // In practice this should not happen since PerformanceLoggers are only called on close() of
      // the PerformanceLogContext, and hence the performance log that gets written by the metric
      // below gets ignored.
      return;
    }

    String requestTag = TraceContext.getTag(TraceRequestListener.TAG_REQUEST).orElse("");
    String pluginTag = TraceContext.getPluginTag().orElse("");
    operationsLatency.record(operation, requestTag, pluginTag, durationMs, TimeUnit.MILLISECONDS);
    operationsCounter.increment(operation, requestTag, pluginTag);
  }
}
