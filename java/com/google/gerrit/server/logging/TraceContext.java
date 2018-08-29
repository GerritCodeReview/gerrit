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
