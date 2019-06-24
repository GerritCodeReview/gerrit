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
    private final Object fieldValue1;
    private final Object fieldValue2;
    private final Object fieldValue3;

    @SuppressWarnings("unchecked")
    <F1, F2, F3> Context(Timer3<F1, F2, F3> timer, F1 f1, F2 f2, F3 f3) {
      this.timer = (Timer3<Object, Object, Object>) timer;
      this.fieldValue1 = f1;
      this.fieldValue2 = f2;
      this.fieldValue3 = f3;
    }

    @Override
    public void record(long elapsed) {
      timer.record(fieldValue1, fieldValue2, fieldValue3, elapsed, NANOSECONDS);
    }
  }

  protected final String name;
  protected final Field<F1> field1;
  protected final Field<F2> field2;
  protected final Field<F3> field3;

  public Timer3(String name, Field<F1> field1, Field<F2> field2, Field<F3> field3) {
    this.name = name;
    this.field1 = field1;
    this.field2 = field2;
    this.field3 = field3;
  }

  /**
   * Begin a timer for the current block, value will be recorded when closed.
   *
   * @param fieldValue1 bucket to record the timer
   * @param fieldValue2 bucket to record the timer
   * @param fieldValue3 bucket to record the timer
   * @return timer context
   */
  public Context start(F1 fieldValue1, F2 fieldValue2, F3 fieldValue3) {
    return new Context(this, fieldValue1, fieldValue2, fieldValue3);
  }

  /**
   * Record a value in the distribution.
   *
   * @param fieldValue1 bucket to record the timer
   * @param fieldValue2 bucket to record the timer
   * @param fieldValue3 bucket to record the timer
   * @param value value to record
   * @param unit time unit of the value
   */
  public final void record(
      F1 fieldValue1, F2 fieldValue2, F3 fieldValue3, long value, TimeUnit unit) {
    long durationMs = unit.toMillis(value);

    LoggingContext.getInstance()
        .addPerformanceLogRecord(
            () ->
                PerformanceLogRecord.create(
                    name,
                    durationMs,
                    field1.name(),
                    fieldValue1,
                    field2.name(),
                    fieldValue2,
                    field3.name(),
                    fieldValue3));

    logger.atFinest().log(
        "%s (%s = %s, %s = %s, %s = %s) took %dms",
        name,
        field1.name(),
        fieldValue1,
        field2.name(),
        fieldValue2,
        field3.name(),
        fieldValue3,
        durationMs);
    doRecord(fieldValue1, fieldValue2, fieldValue3, value, unit);
  }

  /**
   * Record a value in the distribution.
   *
   * @param fieldValue1 bucket to record the timer
   * @param fieldValue2 bucket to record the timer
   * @param fieldValue3 bucket to record the timer
   * @param value value to record
   * @param unit time unit of the value
   */
  protected abstract void doRecord(
      F1 fieldValue1, F2 fieldValue2, F3 fieldValue3, long value, TimeUnit unit);
}
