// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.notedb;

import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Description.Units;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.metrics.Timer0;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/** Metrics for accessing and updating changes in NoteDb. */
@Singleton
class NoteDbMetrics {
  /** End-to-end latency for writing a collection of updates. */
  final Timer0 updateLatency;

  /**
   * The portion of {@link #updateLatency} due to preparing the sequence of updates.
   *
   * <p>May include some I/O (e.g. reading old refs), but excludes writes.
   */
  final Timer0 stageUpdateLatency;

  /** End-to-end latency for reading changes from NoteDb, including reading ref(s) and parsing. */
  final Timer0 readLatency;

  /**
   * The portion of {@link #readLatency} due to parsing commits, but excluding I/O (to a best
   * effort).
   */
  final Timer0 parseLatency;

  @Inject
  NoteDbMetrics(MetricMaker metrics) {
    updateLatency =
        metrics.newTimer(
            "notedb/update_latency",
            new Description("NoteDb update latency for changes")
                .setCumulative()
                .setUnit(Units.MILLISECONDS));

    stageUpdateLatency =
        metrics.newTimer(
            "notedb/stage_update_latency",
            new Description("Latency for staging change updates to NoteDb")
                .setCumulative()
                .setUnit(Units.MICROSECONDS));

    readLatency =
        metrics.newTimer(
            "notedb/read_latency",
            new Description("NoteDb read latency for changes")
                .setCumulative()
                .setUnit(Units.MILLISECONDS));

    parseLatency =
        metrics.newTimer(
            "notedb/parse_latency",
            new Description("NoteDb parse latency for changes")
                .setCumulative()
                .setUnit(Units.MICROSECONDS));
  }
}
