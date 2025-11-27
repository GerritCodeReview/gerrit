// Copyright (C) 2024 The Android Open Source Project
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

package com.google.gerrit.metrics.dropwizard;

import java.time.Duration;

import static com.google.common.truth.Truth.assertThat;

import com.codahale.metrics.MetricRegistry;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Description.Units;
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.MetricsReservoirConfig;
import com.google.gerrit.metrics.ReservoirType;
import com.google.gerrit.metrics.Timer1;

import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;

public class PluginMetricReloadTest {
  private DropWizardMetricMaker metricMaker;
  private MetricRegistry registry;

  @Before
  public void setUp() {
    registry = new MetricRegistry();
    metricMaker = new DropWizardMetricMaker(registry, new TestReservoirConfig());
  }

  @Test
  public void metricsFromNewPluginShouldNotBeRemovedByOldPluginCleanup() {
    // Simulate old plugin creating a metric
    Timer1<String> oldMetric =
        metricMaker.newTimer(
            "plugins/test/latency",
            new Description("Test metric").setCumulative().setUnit(Units.MILLISECONDS),
            Field.ofString("destination", (b, v) -> {}).build());

    // Record a value to ensure metric is registered
    oldMetric.record("target1", 100, TimeUnit.MILLISECONDS);

    // Verify metric appears in metric names (Prometheus would see this)
    assertThat(metricMaker.getMetricNames()).contains("plugins/test/latency");

    // Simulate new plugin loading and creating metric with same name
    // (this happens during plugin reload before old plugin cleanup)
    Timer1<String> newMetric =
        metricMaker.newTimer(
            "plugins/test/latency",
            new Description("Test metric").setCumulative().setUnit(Units.MILLISECONDS),
            Field.ofString("destination", (b, v) -> {}).build());

    // New metric should be usable
    newMetric.record("target1", 200, TimeUnit.MILLISECONDS);

    // Metric should still be visible
    assertThat(metricMaker.getMetricNames()).contains("plugins/test/latency");

    // Simulate old plugin cleanup: old metric's remove() is called
    // This is what PluginMetricMaker.stop() does
    oldMetric.remove();

    // New metric should remain visible because remove() checks
    // instance identity before removing from descriptions map
    assertThat(metricMaker.getMetricNames())
        .contains("plugins/test/latency"); // Should pass with fix, fails without

    // New metric should still work
    newMetric.record("target1", 300, TimeUnit.MILLISECONDS);
  }

  private static class TestReservoirConfig implements MetricsReservoirConfig {
    @Override
    public double reservoirAlpha() {
      return 0.015;
    }

    @Override
    public int reservoirSize() {
      return 1;
    }
  
    @Override
    public Duration reservoirWindow() {
      return Duration.ofSeconds(1);
    }
  
    @Override
    public ReservoirType reservoirType() {
      return ReservoirType.Uniform;
    }
  }
}
