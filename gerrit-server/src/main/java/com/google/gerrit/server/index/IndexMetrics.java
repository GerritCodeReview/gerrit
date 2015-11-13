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

package com.google.gerrit.server.index;

import com.google.gerrit.metrics.Counter0;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Description.Units;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.metrics.Timer0;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
class IndexMetrics {
  final Counter0 updates;
  final Counter0 deletes;
  final Timer0 updateLatency;
  final Timer0 deleteLatency;

  @Inject
  IndexMetrics(MetricMaker metrics) {
    updates = metrics.newCounter(
        "change/index/update_count",
        new Description("Number of updates applied to the index.")
          .setCumulative()
          .setRate());

    deletes = metrics.newCounter(
        "change/index/delete_count",
        new Description("Number of deletes removing changes from the index.")
          .setCumulative()
          .setRate());

    updateLatency = metrics.newTimer(
        "change/index/update_latency",
        new Description("Time to write change to all write versions.")
          .setCumulative()
          .setUnit(Units.MILLISECONDS));

    deleteLatency = metrics.newTimer(
        "change/index/delete_latency",
        new Description("Time to delete change from all write versions.")
          .setCumulative()
          .setUnit(Units.MILLISECONDS));
  }
}
