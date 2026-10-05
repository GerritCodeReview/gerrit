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

package com.google.gerrit.server.logging;

import com.google.gerrit.extensions.annotations.ExtensionPoint;

/**
 * Extension point for receiving synchronous trace span lifecycle events.
 *
 * <p>Invoked synchronously whenever a {@link TraceContext} or {@link TraceContext.TraceTimer} is
 * opened and closed. Implementations can use this to bridge Gerrit tracing into external tracing
 * frameworks (such as Dapper or OpenTelemetry).
 */
@ExtensionPoint
public interface TraceSpanListener {
  /**
   * Called when a trace timer is opened.
   *
   * @param operation name of the operation being timed
   * @param metadata metadata for the operation
   * @return an active {@link TraceSpan}, which will be closed when the timer ends
   */
  default TraceSpan onTimerStart(String operation, Metadata metadata) {
    return TraceSpan.NOOP;
  }

  /**
   * Called when a trace context is opened.
   *
   * @param context the opened trace context
   * @return an active {@link TraceSpan}, which will be closed when the context ends
   */
  default TraceSpan onContextStart(TraceContext context) {
    return TraceSpan.NOOP;
  }

  /** Handle representing an open span. */
  public interface TraceSpan extends AutoCloseable {
    TraceSpan NOOP = () -> {};

    /**
     * Called when a tag is added to the trace context while this span is open.
     *
     * @param name tag name
     * @param value tag value
     */
    default void onTag(String name, String value) {}

    @Override
    void close();
  }
}
