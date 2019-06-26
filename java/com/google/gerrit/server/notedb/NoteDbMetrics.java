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
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.metrics.Timer1;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
class NoteDbMetrics {
  /** End-to-end latency for writing a collection of updates. */
  final Timer1<NoteDbTable> updateLatency;

  /**
   * The portion of {@link #updateLatency} due to preparing the sequence of updates.
   *
   * <p>May include some I/O (e.g. reading old refs), but excludes writes.
   */
  final Timer1<NoteDbTable> stageUpdateLatency;

  /** End-to-end latency for reading changes from NoteDb, including reading ref(s) and parsing. */
  final Timer1<NoteDbTable> readLatency;

  /**
   * The portion of {@link #readLatency} due to parsing commits, but excluding I/O (to a best
   * effort).
   */
  final Timer1<NoteDbTable> parseLatency;

  @Inject
  NoteDbMetrics(MetricMaker metrics) {
    Field<NoteDbTable> tableField = Field.ofEnum(NoteDbTable.class, "table").build();

    updateLatency =
        metrics.newTimer(
            "notedb/update_latency",
            new Description("NoteDb update latency by table")
                .setCumulative()
                .setUnit(Units.MILLISECONDS),
            tableField);

    stageUpdateLatency =
        metrics.newTimer(
            "notedb/stage_update_latency",
            new Description("Latency for staging updates to NoteDb by table")
                .setCumulative()
                .setUnit(Units.MICROSECONDS),
            tableField);

    readLatency =
        metrics.newTimer(
            "notedb/read_latency",
            new Description("NoteDb read latency by table")
                .setCumulative()
                .setUnit(Units.MILLISECONDS),
            tableField);

    parseLatency =
        metrics.newTimer(
            "notedb/parse_latency",
            new Description("NoteDb parse latency by table")
                .setCumulative()
                .setUnit(Units.MICROSECONDS),
            tableField);
  }
}
