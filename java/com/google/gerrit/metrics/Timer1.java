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

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.server.logging.LoggingContext;
import com.google.gerrit.server.logging.PerformanceLogRecord;
import java.util.concurrent.TimeUnit;

/**
 * Records elapsed time for an operation or span.
 *
 * <p>Typical usage in a try-with-resources block:
 *
 * <pre>
 * try (Timer1.Context ctx = timer.start(field)) {
 * }
 * </pre>
 *
 * @param <F1> type of the field.
 */
public abstract class Timer1<F1> implements RegistrationHandle {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static class Context extends TimerContext {
    private final Timer1<Object> timer;
    private final Object field1;

    @SuppressWarnings("unchecked")
    <F1> Context(Timer1<F1> timer, F1 field1) {
      this.timer = (Timer1<Object>) timer;
      this.field1 = field1;
    }

    @Override
    public void record(long elapsed) {
      timer.record(field1, elapsed, NANOSECONDS);
    }
  }

  protected final String name;

  public Timer1(String name) {
    this.name = name;
  }

  /**
   * Begin a timer for the current block, value will be recorded when closed.
   *
   * @param field1 bucket to record the timer
   * @return timer context
   */
  public Context start(F1 field1) {
    return new Context(this, field1);
  }

  /**
   * Record a value in the distribution.
   *
   * @param field1 bucket to record the timer
   * @param value value to record
   * @param unit time unit of the value
   */
  public final void record(F1 field1, long value, TimeUnit unit) {
    long durationMs = unit.toMillis(value);

    // TODO(ekempin): We don't know the field name here. Check whether we can make it available.
    LoggingContext.getInstance()
        .addPerformanceLogRecord(
            () -> PerformanceLogRecord.create(name, durationMs, "field1", field1));

    logger.atFinest().log("%s (%s) took %dms", name, field1, durationMs);
    doRecord(field1, value, unit);
  }

  /**
   * Record a value in the distribution.
   *
   * @param field1 bucket to record the timer
   * @param value value to record
   * @param unit time unit of the value
   */
  protected abstract void doRecord(F1 field1, long value, TimeUnit unit);
}
