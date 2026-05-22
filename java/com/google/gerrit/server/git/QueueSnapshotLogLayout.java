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

import com.google.gerrit.util.logging.LogTimestampFormatter;
import org.apache.log4j.Layout;
import org.apache.log4j.spi.LoggingEvent;
import org.eclipse.jgit.util.QuotedString;

/**
 * Single-line {@code key=value} text layout for the work queue snapshot log.
 *
 * <p>Output is one self-contained event per line so it can be parsed by line-oriented tools (such
 * as {@code grep} and {@code awk}) without multi-line configuration.
 */
final class QueueSnapshotLogLayout extends Layout {
  // Field order is stable so operator-facing log lines remain consistent and diff-able.
  private static final String[] FIELDS = {
    P_EVENT,
    P_SNAPSHOT_ID,
    P_SNAPSHOT_SEQ,
    P_SNAPSHOT_AT,
    P_TASK_ID,
    P_STATE,
    P_QUEUE_NAME,
    P_START_TIME,
    P_DELAY_MS,
    P_PROJECT,
    P_REMOTE_NAME,
    P_TASK_COUNT,
    P_SAMPLE_DURATION_MS,
    P_COMMAND,
  };

  private final LogTimestampFormatter timestampFormatter;

  QueueSnapshotLogLayout() {
    this.timestampFormatter = new LogTimestampFormatter();
  }

  @Override
  public String format(LoggingEvent event) {
    StringBuilder buf = new StringBuilder(192);
    buf.append('[').append(timestampFormatter.format(event.getTimeStamp())).append(']');
    for (String field : FIELDS) {
      Object val = event.getMDC(field);
      if (val == null) {
        continue;
      }
      buf.append(' ').append(field).append('=');
      String s = val.toString();
      if (needsQuoting(s)) {
        buf.append(QuotedString.BOURNE.quote(s));
      } else {
        buf.append(s);
      }
    }
    buf.append('\n');
    return buf.toString();
  }

  private static boolean needsQuoting(String s) {
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == ' ' || c == '"' || c == '\\' || c == '\t' || c == '\n') {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean ignoresThrowable() {
    return true;
  }

  @Override
  public void activateOptions() {}
}
