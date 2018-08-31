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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Strings;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.gerrit.common.Nullable;
import java.util.Optional;

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
 * LoggingContextAwareExecutorService} ensures that the logging context is automatically copied to
 * background threads.
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
   * @param trace whether tracing should be started
   * @param traceId trace ID that should be used for tracing, if {@code null} a trace ID is
   *     generated
   * @param traceIdConsumer consumer for the trace ID, should be used to return the generated trace
   *     ID to the client, not invoked if {@code trace} is {@code false}
   * @return the trace context
   */
  public static TraceContext newTrace(
      boolean trace, @Nullable String traceId, TraceIdConsumer traceIdConsumer) {
    if (!trace) {
      // Create an empty trace context.
      return open();
    }

    if (!Strings.isNullOrEmpty(traceId)) {
      traceIdConsumer.accept(RequestId.Type.TRACE_ID.name(), traceId);
      return open().addTag(RequestId.Type.TRACE_ID, traceId).forceLogging();
    }

    Optional<String> existingTraceId =
        LoggingContext.getInstance()
            .getTagsAsMap()
            .get(RequestId.Type.TRACE_ID.name())
            .stream()
            .findAny();
    if (existingTraceId.isPresent()) {
      // request tracing was already started, no need to generate a new trace ID
      traceIdConsumer.accept(RequestId.Type.TRACE_ID.name(), existingTraceId.get());
      return open();
    }

    RequestId newTraceId = new RequestId();
    traceIdConsumer.accept(RequestId.Type.TRACE_ID.name(), newTraceId.toString());
    return open().addTag(RequestId.Type.TRACE_ID, newTraceId).forceLogging();
  }

  @FunctionalInterface
  public interface TraceIdConsumer {
    void accept(String tagName, String traceId);
  }

  // Table<TAG_NAME, TAG_VALUE, REMOVE_ON_CLOSE>
  private final Table<String, String, Boolean> tags = HashBasedTable.create();

  private boolean stopForceLoggingOnClose;

  private TraceContext() {}

  public TraceContext addTag(RequestId.Type requestId, Object tagValue) {
    return addTag(checkNotNull(requestId, "request ID is required").name(), tagValue);
  }

  public TraceContext addTag(String tagName, Object tagValue) {
    String name = checkNotNull(tagName, "tag name is required");
    String value = checkNotNull(tagValue, "tag value is required").toString();
    tags.put(name, value, LoggingContext.getInstance().addTag(name, value));
    return this;
  }

  public TraceContext forceLogging() {
    if (stopForceLoggingOnClose) {
      return this;
    }

    stopForceLoggingOnClose = !LoggingContext.getInstance().forceLogging(true);
    return this;
  }

  @Override
  public void close() {
    for (Table.Cell<String, String, Boolean> cell : tags.cellSet()) {
      if (cell.getValue()) {
        LoggingContext.getInstance().removeTag(cell.getRowKey(), cell.getColumnKey());
      }
    }
    if (stopForceLoggingOnClose) {
      LoggingContext.getInstance().forceLogging(false);
    }
  }
}
