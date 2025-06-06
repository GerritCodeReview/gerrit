// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.server.logging;

import static java.util.Objects.requireNonNull;

import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Table;
import com.google.common.flogger.FluentLogger;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.server.cancellation.RequestStateContext;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * TraceContext that allows to set logging tags and enforce logging.
 *
 * <p>The logging tags are attached to all log entries that are triggered while the trace context is
 * open. If force logging is enabled all logs that are triggered while the trace context is open are
 * written to the log file regardless of the configured log level.
 *
 * <pre>
 * try (TraceContext traceContext = TraceContext.open()
 *         .addTag("tag-name", "tag-value")
 *         .forceLogging()) {
 *     // This gets logged as: A log [CONTEXT forced=true tag-name="tag-value" ]
 *     // Since force logging is enabled this gets logged independently of the configured log
 *     // level.
 *     logger.atFinest().log("A log");
 *
 *     // do stuff
 * }
 * </pre>
 *
 * <p>The logging tags and the force logging flag are stored in the {@link LoggingContext}. {@link
 * LoggingContextAwareExecutorService}, {@link LoggingContextAwareScheduledExecutorService} and the
 * executor in {@link com.google.gerrit.server.git.WorkQueue} ensure that the logging context is
 * automatically copied to background threads.
 *
 * <p>On close of the trace context newly set tags are unset. Force logging is disabled on close if
 * it got enabled while the trace context was open.
 *
 * <p>Trace contexts can be nested:
 *
 * <pre>
 * // Initially there are no tags
 * logger.atSevere().log("log without tag");
 *
 * // a tag can be set by opening a trace context
 * try (TraceContext ctx = TraceContext.open().addTag("tag1", "value1")) {
 *   logger.atSevere().log("log with tag1=value1");
 *
 *   // while a trace context is open further tags can be added.
 *   ctx.addTag("tag2", "value2")
 *   logger.atSevere().log("log with tag1=value1 and tag2=value2");
 *
 *   // also by opening another trace context a another tag can be added
 *   try (TraceContext ctx2 = TraceContext.open().addTag("tag3", "value3")) {
 *     logger.atSevere().log("log with tag1=value1, tag2=value2 and tag3=value3");
 *
 *     // it's possible to have the same tag name with multiple values
 *     ctx2.addTag("tag3", "value3a")
 *     logger.atSevere().log("log with tag1=value1, tag2=value2, tag3=value3 and tag3=value3a");
 *
 *     // adding a tag with the same name and value as an existing tag has no effect
 *     try (TraceContext ctx3 = TraceContext.open().addTag("tag3", "value3a")) {
 *       logger.atSevere().log("log with tag1=value1, tag2=value2, tag3=value3 and tag3=value3a");
 *     }
 *
 *     // closing ctx3 didn't remove tag3=value3a since it was already set before opening ctx3
 *     logger.atSevere().log("log with tag1=value1, tag2=value2, tag3=value3 and tag3=value3a");
 *   }
 *
 *   // closing ctx2 removed tag3=value3 and tag3-value3a
 *   logger.atSevere().log("with tag1=value1 and tag2=value2");
 * }
 *
 * // closing ctx1 removed tag1=value1 and tag2=value2
 * logger.atSevere().log("log without tag");
 * </pre>
 */
public class TraceContext implements AutoCloseable {
  private static final FluentLogger traceContextLogger = FluentLogger.forEnclosingClass();

  private static final String PLUGIN_TAG = "PLUGIN";

  public static TraceContext open() {
    return new TraceContext();
  }

  /**
   * Opens a new trace context for request tracing.
   *
   * <ul>
   *   <li>sets a tag with a trace ID
   *   <li>enables force logging
   * </ul>
   *
   * <p>if no trace ID is provided a new trace ID is only generated if request tracing was not
   * started yet. If request tracing was already started the given {@code traceIdConsumer} is
   * invoked with the existing trace ID and no new logging tag is set.
   *
   * <p>No-op if {@code trace} is {@code false}.
   *
   * @param forceLogging whether logging should be forced
   * @param traceId trace ID that should be used for tracing, if {@code null} a trace ID is
   *     generated
   * @param traceIdConsumer consumer for the trace ID, should be used to return the generated trace
   *     ID to the client, not invoked if {@code trace} is {@code false}
   * @return the trace context
   */
  public static TraceContext newTrace(
      boolean forceLogging, @Nullable String traceId, TraceIdConsumer traceIdConsumer) {
    String effectiveId;
    if (!Strings.isNullOrEmpty(traceId)) {
      effectiveId = traceId;
    } else {
      Optional<String> existingTraceId =
          LoggingContext.getInstance().getTagsAsMap().get(RequestId.Type.TRACE_ID.name()).stream()
              .findAny();
      effectiveId = existingTraceId.orElse(new RequestId().toString());
    }

    traceIdConsumer.accept(RequestId.Type.TRACE_ID.name(), effectiveId);
    TraceContext traceContext = open().addTag(RequestId.Type.TRACE_ID, effectiveId);
    if (forceLogging) {
      return traceContext.forceLogging();
    }
    return traceContext;
  }

  @FunctionalInterface
  public interface TraceIdConsumer {
    void accept(String tagName, String traceId);
  }

  /**
   * Opens a new timer that logs the time for an operation if request tracing is enabled.
   *
   * @param operation the name of operation the is being performed
   * @return the trace timer
   */
  public static TraceTimer newTimer(String operation) {
    return new TraceTimer(requireNonNull(operation, "operation is required"));
  }

  /**
   * Opens a new timer that logs the time for an operation if request tracing is enabled.
   *
   * @param operation the name of operation the is being performed
   * @param metadata metadata
   * @return the trace timer
   */
  public static TraceTimer newTimer(String operation, Metadata metadata) {
    return new TraceTimer(
        requireNonNull(operation, "operation is required"),
        requireNonNull(metadata, "metadata is required"));
  }

  public static class TraceTimer implements AutoCloseable {
    private static final FluentLogger logger = FluentLogger.forEnclosingClass();

    private final Consumer<Long> doneLogFn;
    private final Stopwatch stopwatch;

    private TraceTimer(String operation) {
      this(
          () -> logger.atFine().log("Starting timer %s", operation),
          elapsedNanos -> {
            LoggingContext.getInstance()
                .addPerformanceLogRecord(
                    () -> PerformanceLogRecord.create(operation, elapsedNanos));
            logger.atFine().log("timer %s took %.2f ms", operation, elapsedNanos / 1000000.0);
          });
    }

    private TraceTimer(String operation, Metadata metadata) {
      this(
          () ->
              logger.atFine().log(
                  "Starting timer %s (%s)", operation, metadata.toStringForLogging()),
          elapsedNanos -> {
            LoggingContext.getInstance()
                .addPerformanceLogRecord(
                    () -> PerformanceLogRecord.create(operation, elapsedNanos, metadata));
            logger.atFine().log(
                "timer %s (%s) took %.2f ms",
                operation, metadata.toStringForLogging(), elapsedNanos / 1000000.0);
          });
    }

    private TraceTimer(Runnable startLogFn, Consumer<Long> doneLogFn) {
      RequestStateContext.abortIfCancelled();
      startLogFn.run();
      this.doneLogFn = doneLogFn;
      this.stopwatch = Stopwatch.createStarted();
    }

    @Override
    public void close() {
      stopwatch.stop();
      doneLogFn.accept(stopwatch.elapsed(TimeUnit.NANOSECONDS));
      RequestStateContext.abortIfCancelled();
    }
  }

  // Table<TAG_NAME, TAG_VALUE, REMOVE_ON_CLOSE>
  private final Table<String, String, Boolean> tags = HashBasedTable.create();

  private final boolean oldAclLogging;
  private final ImmutableList<String> oldAclLogRecords;

  private boolean stopForceLoggingOnClose;
  private boolean stopAclLoggingOnClose;

  private TraceContext() {
    // Just in case remember the old state and reset ACL log entries.
    this.oldAclLogging = LoggingContext.getInstance().isAclLogging();
    this.oldAclLogRecords = LoggingContext.getInstance().getAclLogRecords();
  }

  @CanIgnoreReturnValue
  public TraceContext addTag(RequestId.Type requestId, Object tagValue) {
    return addTag(requireNonNull(requestId, "request ID is required").name(), tagValue);
  }

  @CanIgnoreReturnValue
  public TraceContext addTag(String tagName, Object tagValue) {
    String name = requireNonNull(tagName, "tag name is required");
    String value = requireNonNull(tagValue, "tag value is required").toString();
    tags.put(name, value, LoggingContext.getInstance().addTag(name, value));
    return this;
  }

  public ImmutableSetMultimap<String, String> getTags() {
    ImmutableSetMultimap.Builder<String, String> tagMap = ImmutableSetMultimap.builder();
    tags.cellSet().forEach(c -> tagMap.put(c.getRowKey(), c.getColumnKey()));
    return tagMap.build();
  }

  @CanIgnoreReturnValue
  public TraceContext addPluginTag(String pluginName) {
    return addTag(PLUGIN_TAG, pluginName);
  }

  @CanIgnoreReturnValue
  public TraceContext forceLogging() {
    if (stopForceLoggingOnClose) {
      return this;
    }

    stopForceLoggingOnClose = !LoggingContext.getInstance().forceLogging(true);
    return this;
  }

  public static boolean isTracing() {
    return LoggingContext.getInstance().isLoggingForced();
  }

  public static ImmutableSet<String> getTraceIds() {
    return LoggingContext.getInstance().getTagsAsMap().get(RequestId.Type.TRACE_ID.name());
  }

  public static Optional<String> getPluginTag() {
    return getTag(PLUGIN_TAG);
  }

  public static Optional<String> getTag(String tagName) {
    return LoggingContext.getInstance().getTagsAsMap().get(tagName).stream().findFirst();
  }

  @CanIgnoreReturnValue
  public TraceContext enableAclLogging() {
    if (stopAclLoggingOnClose) {
      return this;
    }

    stopAclLoggingOnClose = !LoggingContext.getInstance().aclLogging(true);
    return this;
  }

  public static ImmutableList<String> getAclLogRecords() {
    return LoggingContext.getInstance().getAclLogRecords();
  }

  @Override
  public void close() {
    try {
      for (Table.Cell<String, String, Boolean> cell : tags.cellSet()) {
        if (cell.getValue()) {
          LoggingContext.getInstance().removeTag(cell.getRowKey(), cell.getColumnKey());
        }
      }
      if (stopForceLoggingOnClose) {
        LoggingContext.getInstance().forceLogging(false);
      }

      if (stopAclLoggingOnClose) {
        LoggingContext.getInstance().aclLogging(oldAclLogging);
        LoggingContext.getInstance().setAclLogRecords(oldAclLogRecords);
      }
    } catch (RuntimeException e) {
      traceContextLogger.atSevere().withCause(e).log("Closing trace context failed");
      throw e;
    }
  }
}
