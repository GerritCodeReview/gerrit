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

import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.server.util.SystemLog;
import com.google.gerrit.util.logging.LogTimestampFormatter;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;

@Singleton
public class WorkQueueSnapshotLogFile implements LifecycleListener {
  private static final Logger log = Logger.getLogger(WorkQueueSnapshotLogFile.class);

  static final String LOG_NAME = "workqueue_log";

  static final String P_SNAPSHOT_SEQ = "snapshotSeq";
  static final String P_TASK_COUNT = "taskCount";
  static final String P_TASK_ID = "taskId";
  static final String P_STATE = "state";
  static final String P_QUEUE_NAME = "queueName";
  static final String P_START_TIME = "startTime";
  static final String P_COMMAND = "command";

  private final LogTimestampFormatter timestampFormatter = new LogTimestampFormatter();

  @Inject
  WorkQueueSnapshotLogFile(SystemLog systemLog, WorkQueueSnapshotConfig config) {
    if (config.getSchedule().isPresent()) {
      initLogger(systemLog);
    }
  }

  @Override
  public void start() {}

  @Override
  public void stop() {
    log.removeAllAppenders();
  }

  void writeSummary(long snapshotSeq, long snapshotAtMs, int taskCount) {
    LoggingEvent e = newEvent(snapshotAtMs);
    e.setProperty(P_SNAPSHOT_SEQ, Long.toString(snapshotSeq));
    e.setProperty(P_TASK_COUNT, Integer.toString(taskCount));
    log.callAppenders(e);
  }

  void write(long snapshotSeq, long snapshotAtMs, TaskInfo task) {
    LoggingEvent e = newEvent(snapshotAtMs);
    e.setProperty(P_SNAPSHOT_SEQ, Long.toString(snapshotSeq));
    e.setProperty(P_TASK_ID, task.id);
    e.setProperty(P_STATE, task.state.name());
    e.setProperty(P_QUEUE_NAME, task.queueName);
    e.setProperty(P_START_TIME, timestampFormatter.format(task.startTime.getTime()));
    if (task.command != null) {
      e.setProperty(P_COMMAND, task.command);
    }
    log.callAppenders(e);
  }

  private static void initLogger(SystemLog systemLog) {
    log.removeAllAppenders();
    log.addAppender(systemLog.createAsyncAppender(LOG_NAME, new WorkQueueSnapshotLogLayout()));
    log.setAdditivity(false);
  }

  private static LoggingEvent newEvent(long timeMs) {
    return new LoggingEvent(
        Logger.class.getName(),
        log,
        timeMs,
        Level.INFO,
        "",
        Thread.currentThread().getName(),
        null,
        null,
        null,
        null);
  }
}
