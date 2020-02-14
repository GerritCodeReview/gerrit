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

import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.flogger.FluentLogger;

/**
 * Wrapper for a {@link Runnable} that copies the {@link LoggingContext} from the current thread to
 * the thread that executes the runnable.
 *
 * <p>The state of the logging context that is copied to the thread that executes the runnable is
 * fixed at the creation time of this wrapper. If the runnable is submitted to an executor and is
 * executed later this means that changes that are done to the logging context in between creating
 * and executing the runnable do not apply.
 *
 * <p>Example:
 *
 * <pre>
 *   try (TraceContext traceContext = TraceContext.newTrace(true, ...)) {
 *     executor
 *         .submit(new LoggingContextAwareRunnable(
 *             () -> {
 *               // Tracing is enabled since the runnable is created within the TraceContext.
 *               // Tracing is even enabled if the executor runs the runnable only after the
 *               // TraceContext was closed.
 *
 *               // The tag "foo=bar" is not set, since it was added to the logging context only
 *               // after this runnable was created.
 *
 *               // do stuff
 *             }))
 *         .get();
 *     traceContext.addTag("foo", "bar");
 *   }
 * </pre>
 *
 * @see LoggingContextAwareCallable
 */
public class LoggingContextAwareRunnable implements Runnable {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Runnable runnable;
  private final Thread callingThread;
  private final ImmutableSetMultimap<String, String> tags;
  private final boolean forceLogging;
  private final boolean performanceLogging;
  private final MutablePerformanceLogRecords mutablePerformanceLogRecords;
  private final boolean aclLogging;
  private final MutableAclLogRecords mutableAclLogRecords;

  /**
   * Creates a LoggingContextAwareRunnable that wraps the given {@link Runnable}.
   *
   * @param runnable Runnable that should be wrapped.
   * @param mutablePerformanceLogRecords instance of {@link MutablePerformanceLogRecords} to which
   *     performance log records that are created from the runnable are added
   * @param mutableAclLogRecords instance of {@link MutableAclLogRecords} to which ACL log records
   *     that are created from the runnable are added
   */
  LoggingContextAwareRunnable(
      Runnable runnable,
      MutablePerformanceLogRecords mutablePerformanceLogRecords,
      MutableAclLogRecords mutableAclLogRecords) {
    this.runnable = runnable;
    this.callingThread = Thread.currentThread();
    this.tags = LoggingContext.getInstance().getTagsAsMap();
    this.forceLogging = LoggingContext.getInstance().isLoggingForced();
    this.performanceLogging = LoggingContext.getInstance().isPerformanceLogging();
    this.mutablePerformanceLogRecords = mutablePerformanceLogRecords;
    this.aclLogging = LoggingContext.getInstance().isAclLogging();
    this.mutableAclLogRecords = mutableAclLogRecords;
  }

  public Runnable unwrap() {
    return runnable;
  }

  @Override
  public void run() {
    if (callingThread.equals(Thread.currentThread())) {
      // propagation of logging context is not needed
      runnable.run();
      return;
    }

    LoggingContext loggingCtx = LoggingContext.getInstance();

    if (!loggingCtx.isEmpty()) {
      logger.atWarning().log("Logging context is not empty: %s", loggingCtx);
    }

    // propagate logging context
    loggingCtx.setTags(tags);
    loggingCtx.forceLogging(forceLogging);
    loggingCtx.performanceLogging(performanceLogging);
    loggingCtx.setMutablePerformanceLogRecords(mutablePerformanceLogRecords);
    loggingCtx.aclLogging(aclLogging);
    loggingCtx.setMutableAclLogRecords(mutableAclLogRecords);
    try {
      runnable.run();
    } finally {
      // Cleanup logging context. This is important if the thread is pooled and reused.
      loggingCtx.clear();
    }
  }
}
