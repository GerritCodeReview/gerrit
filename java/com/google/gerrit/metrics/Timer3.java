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
 * try (Timer3.Context ctx = timer.start(field)) {
 * }
 * </pre>
 *
 * @param <F1> type of the field.
 * @param <F2> type of the field.
 * @param <F3> type of the field.
 */
public abstract class Timer3<F1, F2, F3> implements RegistrationHandle {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static class Context extends TimerContext {
    private final Timer3<Object, Object, Object> timer;
    private final Object field1;
    private final Object field2;
    private final Object field3;

    @SuppressWarnings("unchecked")
    <F1, F2, F3> Context(Timer3<F1, F2, F3> timer, F1 f1, F2 f2, F3 f3) {
      this.timer = (Timer3<Object, Object, Object>) timer;
      this.field1 = f1;
      this.field2 = f2;
      this.field3 = f3;
    }

    @Override
    public void record(long elapsed) {
      timer.record(field1, field2, field3, elapsed, NANOSECONDS);
    }
  }

  protected final String name;

  public Timer3(String name) {
    this.name = name;
  }

  /**
   * Begin a timer for the current block, value will be recorded when closed.
   *
   * @param field1 bucket to record the timer
   * @param field2 bucket to record the timer
   * @param field3 bucket to record the timer
   * @return timer context
   */
  public Context start(F1 field1, F2 field2, F3 field3) {
    return new Context(this, field1, field2, field3);
  }

  /**
   * Record a value in the distribution.
   *
   * @param field1 bucket to record the timer
   * @param field2 bucket to record the timer
   * @param field3 bucket to record the timer
   * @param value value to record
   * @param unit time unit of the value
   */
  public final void record(F1 field1, F2 field2, F3 field3, long value, TimeUnit unit) {
    long durationMs = unit.toMillis(value);

    // TODO(ekempin): We don't know the field names here. Check whether we can make them available.
    LoggingContext.getInstance()
        .addPerformanceLogRecord(
            () ->
                PerformanceLogRecord.create(
                    name, durationMs, "field1", field1, "field2", field2, "field3", field3));

    logger.atFinest().log("%s (%s, %s, %s) took %dms", name, field1, field2, field3, durationMs);
    doRecord(field1, field2, field3, value, unit);
  }

  /**
   * Record a value in the distribution.
   *
   * @param field1 bucket to record the timer
   * @param field2 bucket to record the timer
   * @param field3 bucket to record the timer
   * @param value value to record
   * @param unit time unit of the value
   */
  protected abstract void doRecord(F1 field1, F2 field2, F3 field3, long value, TimeUnit unit);
}
