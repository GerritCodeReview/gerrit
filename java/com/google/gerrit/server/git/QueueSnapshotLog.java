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
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.LogConfig;
import com.google.gerrit.server.config.ScheduleConfig.Schedule;
import com.google.gerrit.server.git.WorkQueue.ProjectTask;
import com.google.gerrit.server.git.WorkQueue.Task;
import com.google.gerrit.server.ioutil.HexFormat;
import com.google.gerrit.server.util.SystemLog;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gerrit.util.logging.LogTimestampFormatter;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.log4j.AsyncAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.eclipse.jgit.lib.Config;

/**
 * Periodically captures a point-in-time snapshot of the {@link WorkQueue} and writes one event per
 * task plus a summary to {@code workqueue_log} (and {@code workqueue_log.json} when {@code
 * log.jsonLogging} is enabled).
 *
 * <p>Only the state of the queue at the instant of the snapshot is recorded; tasks that begin and
 * complete entirely between snapshots are not captured. Each snapshot shares a unique {@code
 * snapshotId} so all rows belonging to the same sample can be correlated when post-processing the
 * log.
 *
 * <p>Disabled by default. To enable, set in {@code gerrit.config}:
 *
 * <pre>
 *   [workQueueSnapshot]
 *     enabled = true
 *     interval = 30 s
 * </pre>
 *
 * <p>The first snapshot is taken one interval after the daemon starts.
 */
@Singleton
public class QueueSnapshotLog implements LifecycleListener, Runnable {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final Logger log = Logger.getLogger(QueueSnapshotLog.class);

  static final String LOG_NAME = "workqueue_log";
  static final String JSON_SUFFIX = ".json";

  static final String P_EVENT = "event";
  static final String P_SNAPSHOT_ID = "snapshotId";
  static final String P_SNAPSHOT_SEQ = "snapshotSeq";
  static final String P_SNAPSHOT_AT = "snapshotAt";
  static final String P_TASK_ID = "taskId";
  static final String P_STATE = "state";
  static final String P_QUEUE_NAME = "queueName";
  static final String P_START_TIME = "startTime";
  static final String P_DELAY_MS = "delayMs";
  static final String P_COMMAND = "command";
  static final String P_PROJECT = "project";
  static final String P_REMOTE_NAME = "remoteName";
  static final String P_TASK_COUNT = "taskCount";
  static final String P_SAMPLE_DURATION_MS = "sampleDurationMs";

  static final String EVENT_TASK = "snapshot_task";
  static final String EVENT_END = "snapshot_end";

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
  private final LogConfig logConfig;
  private final WorkQueue workQueue;
  private final Config cfg;
  private final LogTimestampFormatter timestampFormatter;

  private final boolean enabled;

  private final AtomicLong snapshotSeq = new AtomicLong();

  private volatile AsyncAppender async;

  @Inject
  QueueSnapshotLog(
      SystemLog systemLog,
      LogConfig logConfig,
      WorkQueue workQueue,
      @GerritServerConfig Config cfg) {
    this.systemLog = systemLog;
    this.logConfig = logConfig;
    this.workQueue = workQueue;
    this.cfg = cfg;
    this.timestampFormatter = new LogTimestampFormatter();
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
    AsyncAppender a = async;
    async = null;
    if (a != null) {
      a.close();
    }
  }

  private void enableLogging() {
    AsyncAppender a = new AsyncAppender();
    a.setName(LOG_NAME);
    if (logConfig.isTextLogging()) {
      a.addAppender(systemLog.createAsyncAppender(LOG_NAME, new QueueSnapshotLogLayout()));
    }
    if (logConfig.isJsonLogging()) {
      a.addAppender(
          systemLog.createAsyncAppender(LOG_NAME + JSON_SUFFIX, new QueueSnapshotLogJsonLayout()));
    }
    async = a;
  }

  @Override
  public void run() {
    AsyncAppender a = async;
    if (a == null) {
      return;
    }
    try {
      doSnapshot(a);
    } catch (RuntimeException e) {
      logger.atSevere().withCause(e).log("Work queue snapshot failed");
    }
  }

  private void doSnapshot(AsyncAppender a) {
    String snapshotId = UUID.randomUUID().toString();
    long seq = snapshotSeq.incrementAndGet();
    long snapshotAtMs = TimeUtil.nowMs();
    String snapshotAt = timestampFormatter.format(snapshotAtMs);

    long startNanos = System.nanoTime();
    List<TaskRow> tasks = workQueue.getTaskInfos(TaskRow::new);
    for (TaskRow row : tasks) {
      emitTaskEvent(a, snapshotId, seq, snapshotAt, snapshotAtMs, row);
    }
    long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    emitSummaryEvent(a, snapshotId, seq, snapshotAt, snapshotAtMs, tasks.size(), durationMs);
  }

  private void emitTaskEvent(
      AsyncAppender a,
      String snapshotId,
      long seq,
      String snapshotAt,
      long snapshotAtMs,
      TaskRow row) {
    LoggingEvent e = newEvent(snapshotAtMs, EVENT_TASK);
    setCommonProperties(e, snapshotId, seq, snapshotAt);
    e.setProperty(P_TASK_ID, row.taskId);
    e.setProperty(P_STATE, row.state);
    e.setProperty(P_QUEUE_NAME, row.queueName);
    e.setProperty(P_START_TIME, timestampFormatter.format(row.startTimeMs));
    e.setProperty(P_DELAY_MS, Long.toString(row.delayMs));
    if (row.command != null) {
      e.setProperty(P_COMMAND, row.command);
    }
    if (row.project != null) {
      e.setProperty(P_PROJECT, row.project);
    }
    if (row.remoteName != null) {
      e.setProperty(P_REMOTE_NAME, row.remoteName);
    }
    a.append(e);
  }

  private void emitSummaryEvent(
      AsyncAppender a,
      String snapshotId,
      long seq,
      String snapshotAt,
      long snapshotAtMs,
      int taskCount,
      long sampleDurationMs) {
    LoggingEvent e = newEvent(snapshotAtMs, EVENT_END);
    setCommonProperties(e, snapshotId, seq, snapshotAt);
    e.setProperty(P_TASK_COUNT, Integer.toString(taskCount));
    e.setProperty(P_SAMPLE_DURATION_MS, Long.toString(sampleDurationMs));
    a.append(e);
  }

  private static LoggingEvent newEvent(long timeMs, String message) {
    return new LoggingEvent(
        Logger.class.getName(),
        log,
        timeMs,
        Level.INFO,
        message,
        Thread.currentThread().getName(),
        null,
        null,
        null,
        null);
  }

  private static void setCommonProperties(
      LoggingEvent e, String snapshotId, long seq, String snapshotAt) {
    e.setProperty(P_EVENT, (String) e.getMessage());
    e.setProperty(P_SNAPSHOT_ID, snapshotId);
    e.setProperty(P_SNAPSHOT_SEQ, Long.toString(seq));
    e.setProperty(P_SNAPSHOT_AT, snapshotAt);
  }

  /** Frozen, log-friendly view of a single {@link WorkQueue.Task} at snapshot time. */
  static final class TaskRow {
    final String taskId;
    final String state;
    final String queueName;
    final long startTimeMs;
    final long delayMs;
    final String command;
    final String project;
    final String remoteName;

    TaskRow(Task<?> task) {
      this.taskId = HexFormat.fromInt(task.getTaskId());
      this.state = task.getState().name();
      this.queueName = task.getQueueName();
      this.startTimeMs = task.getStartTime().toEpochMilli();
      this.delayMs = task.getDelay(TimeUnit.MILLISECONDS);
      this.command = task.toString();
      String project = null;
      String remote = null;
      if (task instanceof ProjectTask<?> projectTask) {
        Project.NameKey name = projectTask.getProjectNameKey();
        if (name != null) {
          project = name.get();
        }
        remote = projectTask.getRemoteName();
      }
      this.project = project;
      this.remoteName = remote;
    }
  }
}
