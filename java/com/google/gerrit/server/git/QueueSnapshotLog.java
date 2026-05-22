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

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.ScheduleConfig.Schedule;
import com.google.gerrit.server.git.WorkQueue.Task;
import com.google.gerrit.server.ioutil.HexFormat;
import com.google.gerrit.server.util.SystemLog;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gerrit.util.logging.LogTimestampFormatter;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.log4j.AsyncAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.eclipse.jgit.lib.Config;

/**
 * Periodically captures a point-in-time snapshot of the {@link WorkQueue} and writes one event per
 * task to {@code workqueue_log}.
 *
 * <p>Only the state of the queue at the instant of the snapshot is recorded. Tasks that begin and
 * complete entirely between snapshots are not captured. All rows from a single snapshot share the
 * same line timestamp and a monotonic {@code snapshotSeq}, so they can be correlated and ordered
 * when post-processing the log.
 */
@Singleton
public class QueueSnapshotLog implements LifecycleListener, Runnable {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final Logger log = Logger.getLogger(QueueSnapshotLog.class);

  static final String LOG_NAME = "workqueue_log";

  static final String P_SNAPSHOT_SEQ = "snapshotSeq";
  static final String P_TASK_ID = "taskId";
  static final String P_STATE = "state";
  static final String P_QUEUE_NAME = "queueName";
  static final String P_START_TIME = "startTime";
  static final String P_COMMAND = "command";

  static final String SECTION = "workQueueSnapshot";
  static final String KEY_ENABLED = "enabled";
  static final String KEY_INTERVAL = "interval";
  static final String KEY_START_TIME = "startTime";
  static final String DEFAULT_START_TIME = "00:00";

  public static class Module extends LifecycleModule {
    @Override
    protected void configure() {
      bind(QueueSnapshotLog.class);
      listener().to(QueueSnapshotLog.class);
    }
  }

  private final SystemLog systemLog;
  private final WorkQueue workQueue;
  private final Config cfg;
  private final LogTimestampFormatter timestampFormatter = new LogTimestampFormatter();

  private final boolean enabled;

  private final AtomicLong snapshotSeq = new AtomicLong();

  private volatile AsyncAppender async;

  @Inject
  QueueSnapshotLog(SystemLog systemLog, WorkQueue workQueue, @GerritServerConfig Config cfg) {
    this.systemLog = systemLog;
    this.workQueue = workQueue;
    this.cfg = cfg;
    this.enabled = cfg.getBoolean(SECTION, KEY_ENABLED, false);
  }

  @Override
  public void start() {
    if (!enabled) {
      return;
    }
    long intervalMs;
    try {
      intervalMs =
          ConfigUtil.getTimeUnit(cfg, SECTION, null, KEY_INTERVAL, -1L, TimeUnit.MILLISECONDS);
    } catch (IllegalArgumentException e) {
      logger.atSevere().withCause(e).log(
          "[%s] is enabled but [%s.%s] is invalid; snapshots will not run",
          SECTION, SECTION, KEY_INTERVAL);
      return;
    }
    if (intervalMs <= 0) {
      logger.atWarning().log(
          "[%s] is enabled but [%s.%s] is missing or non-positive; snapshots will not run",
          SECTION, SECTION, KEY_INTERVAL);
      return;
    }
    String startTime = cfg.getString(SECTION, null, KEY_START_TIME);
    if (startTime == null) {
      startTime = DEFAULT_START_TIME;
    }
    Schedule schedule;
    try {
      schedule = Schedule.createOrFail(intervalMs, startTime);
    } catch (IllegalArgumentException e) {
      logger.atSevere().withCause(e).log(
          "Invalid schedule for [%s] (interval=%d ms, startTime=%s); snapshots will not run",
          SECTION, intervalMs, startTime);
      return;
    }
    enableLogging();
    workQueue.scheduleAtFixedRate(this, schedule);
    logger.atInfo().log(
        "Work queue snapshot logging enabled (interval=%d ms, startTime=%s)",
        intervalMs, startTime);
  }

  @Override
  public void stop() {
    AsyncAppender async = this.async;
    this.async = null;
    if (async != null) {
      async.close();
    }
  }

  private void enableLogging() {
    AsyncAppender async = new AsyncAppender();
    async.setName(LOG_NAME);
    async.addAppender(systemLog.createAsyncAppender(LOG_NAME, new QueueSnapshotLogLayout()));
    this.async = async;
  }

  @Override
  public void run() {
    AsyncAppender async = this.async;
    if (async == null) {
      return;
    }
    try {
      doSnapshot(async);
    } catch (RuntimeException e) {
      logger.atSevere().withCause(e).log("Work queue snapshot failed");
    }
  }

  private void doSnapshot(AsyncAppender a) {
    long seq = snapshotSeq.incrementAndGet();
    long snapshotAtMs = TimeUtil.nowMs();
    for (TaskRow row : workQueue.getTaskInfos(TaskRow::new)) {
      emitTaskEvent(a, seq, snapshotAtMs, row);
    }
  }

  private void emitTaskEvent(AsyncAppender a, long seq, long snapshotAtMs, TaskRow row) {
    LoggingEvent e = newEvent(snapshotAtMs);
    e.setProperty(P_SNAPSHOT_SEQ, Long.toString(seq));
    e.setProperty(P_TASK_ID, row.taskId);
    e.setProperty(P_STATE, row.state);
    e.setProperty(P_QUEUE_NAME, row.queueName);
    e.setProperty(P_START_TIME, timestampFormatter.format(row.startTimeMs));
    if (row.command != null) {
      e.setProperty(P_COMMAND, row.command);
    }
    a.append(e);
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

  static final class TaskRow {
    final String taskId;
    final String state;
    final String queueName;
    final long startTimeMs;
    final String command;

    TaskRow(Task<?> task) {
      this.taskId = HexFormat.fromInt(task.getTaskId());
      this.state = task.getState().name();
      this.queueName = task.getQueueName();
      this.startTimeMs = task.getStartTime().toEpochMilli();
      this.command = task.toString();
    }
  }
}
