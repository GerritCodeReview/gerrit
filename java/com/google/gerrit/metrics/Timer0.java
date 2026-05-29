// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.metrics;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.server.cancellation.RequestStateContext;
import com.google.gerrit.server.logging.LoggingContext;
import com.google.gerrit.server.logging.Metadata;
import com.google.gerrit.server.logging.PerformanceLogRecord;
import java.util.concurrent.TimeUnit;

/**
 * Records elapsed time for an operation or span.
 *
 * <p>Typical usage in a try-with-resources block:
 *
 * <pre>
 * try (Timer0.Context ctx = timer.start()) {
 * }
 * </pre>
 */
public abstract class Timer0 implements RegistrationHandle {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static class Context extends TimerContext {
    private final Timer0 timer;

    Context(Timer0 timer) {
      super(timer.name, Metadata.empty());
      this.timer = timer;
    }

    @Override
    public void record(long elapsed, ImmutableList<String> parentOperations, Metadata metadata) {
      timer.record(elapsed, NANOSECONDS, parentOperations, metadata);
    }
  }

  private boolean suppressLogging;

  protected final String name;

  public Timer0(String name) {
    this.name = name;
  }

  /**
   * Begin a timer for the current block, value will be recorded when closed.
   *
   * @return timer context
   */
  public Context start() {
    RequestStateContext.abortIfCancelled();
    if (!suppressLogging) {
      logger.atFine().log("Starting timer %s", name);
    }
    return new Context(this);
  }

  /**
   * Record a value in the distribution.
   *
   * @param value value to record
   * @param unit time unit of the value
   */
  public final void record(long value, TimeUnit unit) {
    record(
        value,
        unit,
        LoggingContext.getInstance().getRunningOperations().toOperationNames(),
        Metadata.empty());
  }

  /**
   * Record a value in the distribution.
   *
   * @param value value to record
   * @param unit time unit of the value
   * @param parentOperations the parent operations that called the operation for which the latency
   *     is recorded by this timer
   * @param metadata metadata that should be recorded/logged
   */
  private final void record(
      long value, TimeUnit unit, ImmutableList<String> parentOperations, Metadata metadata) {
    long durationNanos = unit.toNanos(value);

    if (!suppressLogging) {
      LoggingContext.getInstance()
          .addPerformanceLogRecord(
              () -> PerformanceLogRecord.create(name, durationNanos, parentOperations, metadata));
      logger.atFinest().log("%s took %.2f ms", name, durationNanos / 1000000.0);
    }

    doRecord(value, unit);
    RequestStateContext.abortIfCancelled();
  }

  /** Suppress logging (debug log and performance log) when values are recorded. */
  public final Timer0 suppressLogging() {
    this.suppressLogging = true;
    return this;
  }

  /**
   * Record a value in the distribution.
   *
   * @param value value to record
   * @param unit time unit of the value
   */
  protected abstract void doRecord(long value, TimeUnit unit);
}
