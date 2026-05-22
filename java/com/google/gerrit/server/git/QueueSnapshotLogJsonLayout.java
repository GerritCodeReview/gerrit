// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.server.git;

import static com.google.gerrit.server.git.QueueSnapshotLog.P_COMMAND;
import static com.google.gerrit.server.git.QueueSnapshotLog.P_DELAY_MS;
import static com.google.gerrit.server.git.QueueSnapshotLog.P_EVENT;
import static com.google.gerrit.server.git.QueueSnapshotLog.P_PROJECT;
import static com.google.gerrit.server.git.QueueSnapshotLog.P_QUEUE_NAME;
import static com.google.gerrit.server.git.QueueSnapshotLog.P_REMOTE_NAME;
import static com.google.gerrit.server.git.QueueSnapshotLog.P_SAMPLE_DURATION_MS;
import static com.google.gerrit.server.git.QueueSnapshotLog.P_SNAPSHOT_AT;
import static com.google.gerrit.server.git.QueueSnapshotLog.P_SNAPSHOT_ID;
import static com.google.gerrit.server.git.QueueSnapshotLog.P_SNAPSHOT_SEQ;
import static com.google.gerrit.server.git.QueueSnapshotLog.P_START_TIME;
import static com.google.gerrit.server.git.QueueSnapshotLog.P_STATE;
import static com.google.gerrit.server.git.QueueSnapshotLog.P_TASK_COUNT;
import static com.google.gerrit.server.git.QueueSnapshotLog.P_TASK_ID;

import com.google.gerrit.util.logging.JsonLayout;
import com.google.gerrit.util.logging.JsonLogEntry;
import com.google.gson.annotations.SerializedName;
import org.apache.log4j.spi.LoggingEvent;

/**
 * Newline-delimited JSON layout for the work queue snapshot log.
 *
 * <p>Field names are emitted in {@code snake_case} (per {@link JsonLayout}'s GSON configuration)
 * and null fields are omitted, so each line is a compact self-contained event ready for ingestion
 * by tools that auto-extract JSON fields.
 */
public final class QueueSnapshotLogJsonLayout extends JsonLayout {

  @Override
  public JsonLogEntry toJsonLogEntry(LoggingEvent event) {
    return new QueueSnapshotJsonLogEntry(event);
  }

  @SuppressWarnings("unused")
  private final class QueueSnapshotJsonLogEntry extends JsonLogEntry {
    @SerializedName("@timestamp")
    public String timestamp;

    @SerializedName("@version")
    public final int version = 1;

    public String event;
    public String snapshotId;
    public String snapshotSeq;
    public String snapshotAt;
    public String taskId;
    public String state;
    public String queueName;
    public String startTime;
    public String delayMs;
    public String command;
    public String project;
    public String remoteName;
    public String taskCount;
    public String sampleDurationMs;

    QueueSnapshotJsonLogEntry(LoggingEvent e) {
      this.timestamp = timestampFormatter.format(e.getTimeStamp());
      this.event = getMdcString(e, P_EVENT);
      this.snapshotId = getMdcString(e, P_SNAPSHOT_ID);
      this.snapshotSeq = getMdcString(e, P_SNAPSHOT_SEQ);
      this.snapshotAt = getMdcString(e, P_SNAPSHOT_AT);
      this.taskId = getMdcString(e, P_TASK_ID);
      this.state = getMdcString(e, P_STATE);
      this.queueName = getMdcString(e, P_QUEUE_NAME);
      this.startTime = getMdcString(e, P_START_TIME);
      this.delayMs = getMdcString(e, P_DELAY_MS);
      this.command = getMdcString(e, P_COMMAND);
      this.project = getMdcString(e, P_PROJECT);
      this.remoteName = getMdcString(e, P_REMOTE_NAME);
      this.taskCount = getMdcString(e, P_TASK_COUNT);
      this.sampleDurationMs = getMdcString(e, P_SAMPLE_DURATION_MS);
    }
  }
}
