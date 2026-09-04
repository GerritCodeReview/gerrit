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

import static com.google.gerrit.server.git.WorkQueueSnapshotLogFile.P_COMMAND;
import static com.google.gerrit.server.git.WorkQueueSnapshotLogFile.P_QUEUE_NAME;
import static com.google.gerrit.server.git.WorkQueueSnapshotLogFile.P_SNAPSHOT_SEQ;
import static com.google.gerrit.server.git.WorkQueueSnapshotLogFile.P_START_TIME;
import static com.google.gerrit.server.git.WorkQueueSnapshotLogFile.P_STATE;
import static com.google.gerrit.server.git.WorkQueueSnapshotLogFile.P_TASK_COUNT;
import static com.google.gerrit.server.git.WorkQueueSnapshotLogFile.P_TASK_ID;

import com.google.gerrit.util.logging.LogTimestampFormatter;
import org.apache.log4j.Layout;
import org.apache.log4j.spi.LoggingEvent;
import org.eclipse.jgit.util.QuotedString;

final class WorkQueueSnapshotLogLayout extends Layout {
  private static final String[] FIELDS = {
    P_SNAPSHOT_SEQ, P_TASK_COUNT, P_TASK_ID, P_STATE, P_QUEUE_NAME, P_START_TIME, P_COMMAND,
  };

  private final LogTimestampFormatter timestampFormatter;

  WorkQueueSnapshotLogLayout() {
    this.timestampFormatter = new LogTimestampFormatter();
  }

  @Override
  public String format(LoggingEvent event) {
    StringBuilder buf = new StringBuilder(128);
    buf.append('[').append(timestampFormatter.format(event.getTimeStamp())).append(']');
    for (String field : FIELDS) {
      Object val = event.getMDC(field);
      if (val == null) {
        continue;
      }
      buf.append(' ').append(field).append('=');
      String s = val.toString();
      if (P_COMMAND.equals(field)) {
        buf.append(QuotedString.BOURNE.quote(s));
      } else {
        buf.append(s);
      }
    }
    buf.append('\n');
    return buf.toString();
  }

  @Override
  public boolean ignoresThrowable() {
    return true;
  }

  @Override
  public void activateOptions() {}
}
