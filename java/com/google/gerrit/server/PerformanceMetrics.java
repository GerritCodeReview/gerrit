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

import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.metrics.Timer3;
import com.google.gerrit.server.logging.Metadata;
import com.google.gerrit.server.logging.PerformanceLogger;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.concurrent.TimeUnit;

/** Performance logger that records the execution times as a metric. */
@Singleton
public class PerformanceMetrics implements PerformanceLogger {
  public final Timer3<String, String, Integer> operationsLatency;

  @Inject
  PerformanceMetrics(MetricMaker metricMaker) {
    this.operationsLatency =
        metricMaker.newTimer(
            "performance/operations",
            new Description("Latency of performing operations")
                .setCumulative()
                .setUnit(Description.Units.MILLISECONDS),
            Field.ofString(
                    "operation_name",
                    (metadataBuilder, fieldValue) -> metadataBuilder.operationName(fieldValue))
                .build(),
            Field.ofString(
                    "project_name",
                    (metadataBuilder, fieldValue) -> metadataBuilder.projectName(fieldValue))
                .build(),
            Field.ofInteger(
                    "change_id",
                    (metadataBuilder, fieldValue) -> metadataBuilder.changeId(fieldValue))
                .build());
  }

  @Override
  public void log(String operation, long durationMs, Metadata metadata) {
    operationsLatency.record(
        operation,
        metadata.projectName().orElse(""),
        metadata.changeId().orElse(0),
        durationMs,
        TimeUnit.MILLISECONDS);
  }
}
