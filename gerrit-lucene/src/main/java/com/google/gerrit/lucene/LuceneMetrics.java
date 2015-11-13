// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.lucene;

import com.google.gerrit.metrics.CallbackMetric0;
import com.google.gerrit.metrics.Counter0;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Description.Units;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.metrics.Timer0;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
class LuceneMetrics {
  private final MetricMaker metrics;
  final Timer0 buildLatency;
  final IndexMetrics open;
  final IndexMetrics closed;

  @Inject
  LuceneMetrics(MetricMaker metrics) {
    this.metrics = metrics;
    buildLatency = metrics.newTimer(
        "change/lucene/build_latency",
        new Description("Time constructing a change document for indexing.")
          .setCumulative()
          .setUnit(Units.MILLISECONDS));

    open = new IndexMetrics("changes_open", metrics);
    closed = new IndexMetrics("changes_closed", metrics);
  }

  IndexMetrics forIndex(String name) {
    if (name.equals("changes_open")) {
      return open;
    } else if (name.equals("changes_closed")) {
      return closed;
    }
    throw new IllegalArgumentException("unknown index " + name);
  }

  class IndexMetrics {
    final Timer0 autoCommit;
    final Timer0 commitLatency;
    final Timer0 statLatency;
    final Counter0 commitTimeout;
    final CallbackMetric0<Long> count;

    IndexMetrics(String name, MetricMaker metrics) {
      autoCommit = metrics.newTimer(
          String.format("change/lucene/%s/background_commit_latency", name),
          new Description("Time spent committing index in the background.")
            .setCumulative()
            .setUnit(Units.MICROSECONDS));

      commitLatency = metrics.newTimer(
          String.format("change/lucene/%s/commit_latency", name),
          new Description("Time waiting for index to make mutation visible.")
            .setCumulative()
            .setUnit(Units.MICROSECONDS));

      commitTimeout = metrics.newCounter(
          String.format("change/lucene/%s/commit_timeout", name),
          new Description("Timeouts while waiting for commit.")
            .setCumulative());

      statLatency = metrics.newTimer(
          String.format("change/lucene/%s/stat_latency", name),
          new Description("Time reading index statistics for monitoring.")
            .setCumulative()
            .setUnit(Units.MICROSECONDS));

      count = metrics.newCallbackMetric(
          String.format("change/lucene/%s/count", name),
          Long.class,
          new Description("Number of changes stored by the subindex.")
            .setGauge()
            .setUnit("changes"));
    }

    void countTrigger(Runnable trigger) {
      metrics.newTrigger(count, trigger);
    }
  }
}
