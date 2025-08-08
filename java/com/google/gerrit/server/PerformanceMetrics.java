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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.metrics.Counter3;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.metrics.Timer3;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.logging.Metadata;
import com.google.gerrit.server.logging.PerformanceLogger;
import com.google.gerrit.server.logging.TraceContext;
import com.google.inject.Inject;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.Config;

/** Performance logger that records the execution times as a metric. */
public class PerformanceMetrics implements PerformanceLogger {
  private static final String OPERATION_LATENCY_METRIC_NAME = "performance/operations";
  private static final String OPERATION_COUNT_METRIC_NAME = "performance/operations_count";
  private static final String OPERATIONS_PER_ENDPOINT_LATENCY_METRIC_NAME =
      "performance/operations-per-endpoint";

  private final ImmutableList<String> tracedOperations;
  private final Timer3<String, String, String> operationsLatency;
  private final Counter3<String, String, String> operationsCounter;
  private final Timer3<String, String, String> operationsEndpointLatency;

  private final Map<MetricKey, Long> perRequestLatencyNanos = new HashMap<>();

  @Inject
  PerformanceMetrics(@GerritServerConfig Config cfg, MetricMaker metricMaker) {
    this.tracedOperations =
        ImmutableList.copyOf(cfg.getStringList("performance", "metric", "operation"));

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
        metricMaker
            .newTimer(
                OPERATION_LATENCY_METRIC_NAME,
                new Description("Latency of performing operations")
                    .setCumulative()
                    .setUnit(Description.Units.MILLISECONDS),
                operationNameField,
                requestField,
                pluginField)
            .suppressLogging();
    this.operationsCounter =
        metricMaker.newCounter(
            OPERATION_COUNT_METRIC_NAME,
            new Description("Number of performed operations").setRate(),
            operationNameField,
            requestField,
            pluginField);
    this.operationsEndpointLatency =
        metricMaker
            .newTimer(
                OPERATIONS_PER_ENDPOINT_LATENCY_METRIC_NAME,
                new Description("Per endpoint latency of performing operations")
                    .setCumulative()
                    .setUnit(Description.Units.MILLISECONDS),
                operationNameField,
                requestField,
                pluginField)
            .suppressLogging();
  }

  @Override
  public void logNanos(String operation, long durationNanos, Instant endTime) {
    logNanos(operation, durationNanos, endTime, /* metadata= */ null);
  }

  @Override
  public void logNanos(
      String operation, long durationNanos, Instant endTime, @Nullable Metadata metadata) {
    if (!tracedOperations.contains(operation)) {
      return;
    }

    String requestTag = TraceContext.getTag(TraceRequestListener.TAG_REQUEST).orElse("");
    String pluginTag = TraceContext.getPluginTag().orElse("");
    operationsLatency.record(operation, requestTag, pluginTag, durationNanos, TimeUnit.NANOSECONDS);
    operationsCounter.increment(operation, requestTag, pluginTag);

    perRequestLatencyNanos.compute(
        MetricKey.create(operation, requestTag, pluginTag),
        (metricKey, latencyNanos) ->
            (latencyNanos == null) ? durationNanos : latencyNanos + durationNanos);
  }

  @Override
  public void done() {
    perRequestLatencyNanos.forEach(
        (metricKey, latencyNanos) ->
            operationsEndpointLatency.record(
                metricKey.operation(),
                metricKey.requestTag(),
                metricKey.pluginTag(),
                latencyNanos,
                TimeUnit.NANOSECONDS));
    perRequestLatencyNanos.clear();
  }

  @AutoValue
  abstract static class MetricKey {
    abstract String operation();

    abstract String requestTag();

    abstract String pluginTag();

    public static MetricKey create(String operation, String requestTag, String pluginTag) {
      return new AutoValue_PerformanceMetrics_MetricKey(operation, requestTag, pluginTag);
    }
  }
}
