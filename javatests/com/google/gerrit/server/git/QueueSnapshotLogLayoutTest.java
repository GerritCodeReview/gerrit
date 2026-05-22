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

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.server.git.QueueSnapshotLog.EVENT_END;
import static com.google.gerrit.server.git.QueueSnapshotLog.EVENT_TASK;
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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.Test;

public class QueueSnapshotLogLayoutTest {
  private static final Logger LOG = Logger.getLogger(QueueSnapshotLogLayoutTest.class);

  @Test
  public void textLayoutEmitsSingleLineKeyValuesForTaskEvent() {
    String out = new QueueSnapshotLogLayout().format(taskEvent());

    assertThat(out).endsWith("\n");
    assertThat(out.indexOf('\n')).isEqualTo(out.length() - 1);
    assertThat(out).contains("event=snapshot_task");
    assertThat(out).contains("snapshot_id=abc-123");
    assertThat(out).contains("snapshot_seq=42");
    assertThat(out).contains("task_id=deadbeef");
    assertThat(out).contains("state=SLEEPING");
    assertThat(out).contains("queue_name=WorkQueue");
    assertThat(out).contains("delay_ms=600000");
    assertThat(out).contains("project=tools/gerrit");
    assertThat(out).contains("remote_name=dst1");
    // Command contains a space, so it must be quoted.
    assertThat(out).contains("command=\"mirror dst1:/home/git/tools/gerrit.git\"");
  }

  @Test
  public void textLayoutEmitsSingleLineKeyValuesForSummaryEvent() {
    String out = new QueueSnapshotLogLayout().format(summaryEvent(/* taskCount= */ 0));

    assertThat(out).endsWith("\n");
    assertThat(out.indexOf('\n')).isEqualTo(out.length() - 1);
    assertThat(out).contains("event=snapshot_end");
    assertThat(out).contains("snapshot_id=abc-123");
    assertThat(out).contains("task_count=0");
    assertThat(out).contains("sample_duration_ms=7");
    // Task-only fields must not appear on the summary event.
    assertThat(out).doesNotContain("task_id=");
    assertThat(out).doesNotContain("state=");
  }

  @Test
  public void textLayoutOmitsMissingFields() {
    LoggingEvent e = newEvent(EVENT_TASK);
    e.setProperty(P_EVENT, EVENT_TASK);
    e.setProperty(P_SNAPSHOT_ID, "abc-123");
    e.setProperty(P_SNAPSHOT_SEQ, "1");
    e.setProperty(P_SNAPSHOT_AT, "2026-05-22T13:43:01.512-07:00");
    e.setProperty(P_TASK_ID, "deadbeef");
    e.setProperty(P_STATE, "RUNNING");
    e.setProperty(P_QUEUE_NAME, "WorkQueue");
    e.setProperty(P_START_TIME, "2026-05-22T13:31:15.435-07:00");
    e.setProperty(P_DELAY_MS, "0");

    String out = new QueueSnapshotLogLayout().format(e);

    assertThat(out).doesNotContain("project=");
    assertThat(out).doesNotContain("remote_name=");
    assertThat(out).doesNotContain("command=");
  }

  @Test
  public void jsonLayoutEmitsExpectedFieldsForTaskEvent() {
    String out = new QueueSnapshotLogJsonLayout().format(taskEvent());

    assertThat(out).endsWith("\n");
    JsonObject obj = JsonParser.parseString(out).getAsJsonObject();
    assertThat(obj.get("@version").getAsInt()).isEqualTo(1);
    assertThat(obj.get("@timestamp").getAsString()).isNotEmpty();
    assertThat(obj.get("event").getAsString()).isEqualTo(EVENT_TASK);
    assertThat(obj.get("snapshot_id").getAsString()).isEqualTo("abc-123");
    assertThat(obj.get("snapshot_seq").getAsString()).isEqualTo("42");
    assertThat(obj.get("task_id").getAsString()).isEqualTo("deadbeef");
    assertThat(obj.get("state").getAsString()).isEqualTo("SLEEPING");
    assertThat(obj.get("queue_name").getAsString()).isEqualTo("WorkQueue");
    assertThat(obj.get("delay_ms").getAsString()).isEqualTo("600000");
    assertThat(obj.get("project").getAsString()).isEqualTo("tools/gerrit");
    assertThat(obj.get("remote_name").getAsString()).isEqualTo("dst1");
    assertThat(obj.get("command").getAsString())
        .isEqualTo("mirror dst1:/home/git/tools/gerrit.git");

    // Task-only event must not carry summary-only fields.
    assertThat(obj.has("task_count")).isFalse();
    assertThat(obj.has("sample_duration_ms")).isFalse();
  }

  @Test
  public void jsonLayoutEmitsExpectedFieldsForSummaryEvent() {
    String out = new QueueSnapshotLogJsonLayout().format(summaryEvent(/* taskCount= */ 3));

    JsonObject obj = JsonParser.parseString(out).getAsJsonObject();
    assertThat(obj.get("event").getAsString()).isEqualTo(EVENT_END);
    assertThat(obj.get("task_count").getAsString()).isEqualTo("3");
    assertThat(obj.get("sample_duration_ms").getAsString()).isEqualTo("7");

    // Summary event should not carry per-task fields.
    assertThat(obj.has("task_id")).isFalse();
    assertThat(obj.has("state")).isFalse();
    assertThat(obj.has("project")).isFalse();
  }

  @Test
  public void jsonLayoutProducesOneJsonObjectPerLine() {
    String taskLine = new QueueSnapshotLogJsonLayout().format(taskEvent());
    String summaryLine = new QueueSnapshotLogJsonLayout().format(summaryEvent(/* taskCount= */ 1));

    String combined = taskLine + summaryLine;
    String[] lines = combined.split("\n");
    assertThat(lines).hasLength(2);
    for (String line : lines) {
      // Each line must parse independently.
      JsonParser.parseString(line).getAsJsonObject();
    }
  }

  private static LoggingEvent taskEvent() {
    LoggingEvent e = newEvent(EVENT_TASK);
    e.setProperty(P_EVENT, EVENT_TASK);
    e.setProperty(P_SNAPSHOT_ID, "abc-123");
    e.setProperty(P_SNAPSHOT_SEQ, "42");
    e.setProperty(P_SNAPSHOT_AT, "2026-05-22T13:43:01.512-07:00");
    e.setProperty(P_TASK_ID, "deadbeef");
    e.setProperty(P_STATE, "SLEEPING");
    e.setProperty(P_QUEUE_NAME, "WorkQueue");
    e.setProperty(P_START_TIME, "2026-05-22T13:31:15.435-07:00");
    e.setProperty(P_DELAY_MS, "600000");
    e.setProperty(P_COMMAND, "mirror dst1:/home/git/tools/gerrit.git");
    e.setProperty(P_PROJECT, "tools/gerrit");
    e.setProperty(P_REMOTE_NAME, "dst1");
    return e;
  }

  private static LoggingEvent summaryEvent(int taskCount) {
    LoggingEvent e = newEvent(EVENT_END);
    e.setProperty(P_EVENT, EVENT_END);
    e.setProperty(P_SNAPSHOT_ID, "abc-123");
    e.setProperty(P_SNAPSHOT_SEQ, "42");
    e.setProperty(P_SNAPSHOT_AT, "2026-05-22T13:43:01.512-07:00");
    e.setProperty(P_TASK_COUNT, Integer.toString(taskCount));
    e.setProperty(P_SAMPLE_DURATION_MS, "7");
    return e;
  }

  private static LoggingEvent newEvent(String message) {
    return new LoggingEvent(
        Logger.class.getName(),
        LOG,
        System.currentTimeMillis(),
        Level.INFO,
        message,
        Thread.currentThread().getName(),
        null,
        null,
        null,
        null);
  }
}
