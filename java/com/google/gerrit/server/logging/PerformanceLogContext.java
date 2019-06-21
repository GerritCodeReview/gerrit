// Copyright (C) 2019 The Android Open Source Project
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.registration.Extension;
import org.eclipse.jgit.lib.Config;

/**
 * Context for capturing performance log records. When the context is closed the performance log
 * records are handed over to the registered {@link PerformanceLogger}s.
 *
 * <p>Capturing performance log records is disabled if there are no {@link PerformanceLogger}
 * registered (in this case the captured performance log records would never be used).
 *
 * <p>It's important to enable capturing of performance log records in a context that ensures to
 * consume the captured performance log records. Otherwise captured performance log records might
 * leak into other requests that are executed by the same thread (if a thread pool is used to
 * process requests).
 */
public class PerformanceLogContext implements AutoCloseable {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  // Do not use PluginSetContext. PluginSetContext traces the plugin latency with a timer metric
  // which would result in a performance log and we don't want to log the performance of writing
  // a performance log in the performance log (endless loop).
  private final DynamicSet<PerformanceLogger> performanceLoggers;

  private final boolean oldPerformanceLogging;
  private final ImmutableList<PerformanceLogRecord> oldPerformanceLogRecords;

  public PerformanceLogContext(
      Config gerritConfig, DynamicSet<PerformanceLogger> performanceLoggers) {
    this.performanceLoggers = performanceLoggers;

    // Just in case remember the old state and reset performance log entries.
    this.oldPerformanceLogging = LoggingContext.getInstance().isPerformanceLogging();
    this.oldPerformanceLogRecords = LoggingContext.getInstance().getPerformanceLogRecords();
    LoggingContext.getInstance().clearPerformanceLogEntries();

    // Do not create performance log entries if performance logging is disabled or if no
    // PerformanceLogger is registered.
    boolean enablePerformanceLogging =
        gerritConfig.getBoolean("tracing", "performanceLogging", true);
    LoggingContext.getInstance()
        .performanceLogging(
            enablePerformanceLogging && !Iterables.isEmpty(performanceLoggers.entries()));
  }

  @Override
  public void close() {
    if (LoggingContext.getInstance().isPerformanceLogging()) {
      runEach(performanceLoggers, LoggingContext.getInstance().getPerformanceLogRecords());
    }

    // Restore old state. Required to support nesting of PerformanceLogContext's.
    LoggingContext.getInstance().performanceLogging(oldPerformanceLogging);
    LoggingContext.getInstance().setPerformanceLogRecords(oldPerformanceLogRecords);
  }

  /**
   * Invokes all performance loggers.
   *
   * <p>Similar to how {@code com.google.gerrit.server.plugincontext.PluginContext} invokes plugins
   * but without recording metrics for invoking {@link PerformanceLogger}s.
   *
   * @param performanceLoggers the performance loggers that should be invoked
   * @param performanceLogRecords the performance log records that should be handed over to the
   *     performance loggers
   */
  private static void runEach(
      DynamicSet<PerformanceLogger> performanceLoggers,
      ImmutableList<PerformanceLogRecord> performanceLogRecords) {
    performanceLoggers
        .entries()
        .forEach(
            p -> {
              try (TraceContext traceContext = newPluginTrace(p)) {
                performanceLogRecords.forEach(r -> r.writeTo(p.get()));
              } catch (Throwable e) {
                logger.atWarning().withCause(e).log(
                    "Failure in %s of plugin %s", p.get().getClass(), p.getPluginName());
              }
            });
  }

  /**
   * Opens a trace context for a plugin that implements {@link PerformanceLogger}.
   *
   * <p>Basically the same as {@code
   * com.google.gerrit.server.plugincontext.PluginContext#newTrace(Extension<T>)}. We have this
   * method here to avoid a dependency on PluginContext which lives in
   * "//java/com/google/gerrit/server". This package ("//java/com/google/gerrit/server/logging")
   * should have as few dependencies as possible.
   *
   * @param extension performance logger extension
   * @return the trace context
   */
  private static TraceContext newPluginTrace(Extension<PerformanceLogger> extension) {
    return TraceContext.open().addPluginTag(extension.getPluginName());
  }
}
