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
import java.util.concurrent.Callable;

/**
 * Wrapper for a {@link Callable} that copies the {@link LoggingContext} from the current thread to
 * the thread that executes the callable.
 *
 * <p>The state of the logging context that is copied to the thread that executes the callable is
 * fixed at the creation time of this wrapper. If the callable is submitted to an executor and is
 * executed later this means that changes that are done to the logging context in between creating
 * and executing the callable do not apply.
 *
 * <p>See {@link LoggingContextAwareRunnable} for an example.
 *
 * @see LoggingContextAwareRunnable
 */
class LoggingContextAwareCallable<T> implements Callable<T> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Callable<T> callable;
  private final Thread callingThread;
  private final ImmutableSetMultimap<String, String> tags;
  private final boolean forceLogging;
  private final boolean performanceLogging;
  private final MutablePerformanceLogRecords mutablePerformanceLogRecords;
  private final boolean aclLogging;
  private final MutableAclLogRecords mutableAclLogRecords;

  /**
   * Creates a LoggingContextAwareCallable that wraps the given {@link Callable}.
   *
   * @param callable Callable that should be wrapped.
   * @param mutablePerformanceLogRecords instance of {@link MutablePerformanceLogRecords} to which
   *     performance log records that are created from the runnable are added
   * @param mutableAclLogRecords instance of {@link MutableAclLogRecords} to which ACL log records
   *     that are created from the runnable are added
   */
  LoggingContextAwareCallable(
      Callable<T> callable,
      MutablePerformanceLogRecords mutablePerformanceLogRecords,
      MutableAclLogRecords mutableAclLogRecords) {
    this.callable = callable;
    this.callingThread = Thread.currentThread();
    this.tags = LoggingContext.getInstance().getTagsAsMap();
    this.forceLogging = LoggingContext.getInstance().isLoggingForced();
    this.performanceLogging = LoggingContext.getInstance().isPerformanceLogging();
    this.mutablePerformanceLogRecords = mutablePerformanceLogRecords;
    this.aclLogging = LoggingContext.getInstance().isAclLogging();
    this.mutableAclLogRecords = mutableAclLogRecords;
  }

  @Override
  public T call() throws Exception {
    if (callingThread.equals(Thread.currentThread())) {
      // propagation of logging context is not needed
      return callable.call();
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
      return callable.call();
    } finally {
      // Cleanup logging context. This is important if the thread is pooled and reused.
      loggingCtx.clear();
    }
  }
}
