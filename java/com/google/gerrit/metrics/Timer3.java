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

  public static class Context<F1, F2, F3> extends TimerContext {
    private final Timer3<F1, F2, F3> timer;
    private final F1 fieldValue1;
    private final F2 fieldValue2;
    private final F3 fieldValue3;

    Context(Timer3<F1, F2, F3> timer, Metadata metadata, F1 f1, F2 f2, F3 f3) {
      super(timer.name, metadata);
      this.timer = timer;
      this.fieldValue1 = f1;
      this.fieldValue2 = f2;
      this.fieldValue3 = f3;
    }

    @Override
    public void record(long elapsed, ImmutableList<String> parentOperations, Metadata metadata) {
      timer.record(
          fieldValue1, fieldValue2, fieldValue3, elapsed, NANOSECONDS, parentOperations, metadata);
    }
  }

  private boolean suppressLogging;

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
  public Context<F1, F2, F3> start(F1 fieldValue1, F2 fieldValue2, F3 fieldValue3) {
    RequestStateContext.abortIfCancelled();
    if (!suppressLogging) {
      logger.atFine().log(
          "Starting timer %s (%s = %s, %s = %s, %s = %s)",
          name, field1.name(), fieldValue1, field2.name(), fieldValue2, field3.name(), fieldValue3);
    }
    return new Context<>(
        this,
        getMetadata(fieldValue1, fieldValue2, fieldValue3),
        fieldValue1,
        fieldValue2,
        fieldValue3);
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
    record(
        fieldValue1,
        fieldValue2,
        fieldValue3,
        value,
        unit,
        LoggingContext.getInstance().getRunningOperations().toOperationNames(),
        getMetadata(fieldValue1, fieldValue2, fieldValue3));
  }

  /**
   * Record a value in the distribution.
   *
   * @param fieldValue1 bucket to record the timer
   * @param fieldValue2 bucket to record the timer
   * @param fieldValue3 bucket to record the timer
   * @param value value to record
   * @param unit time unit of the value
   * @param parentOperations the parent operations that called the operation for which the latency
   *     is recorded by this timer
   * @param metadata metadata that should be recorded/logged
   */
  private final void record(
      F1 fieldValue1,
      F2 fieldValue2,
      F3 fieldValue3,
      long value,
      TimeUnit unit,
      ImmutableList<String> parentOperations,
      Metadata metadata) {
    long durationNanos = unit.toNanos(value);

    if (!suppressLogging) {
      LoggingContext.getInstance()
          .addPerformanceLogRecord(
              () -> PerformanceLogRecord.create(name, durationNanos, parentOperations, metadata));
      logger.atFinest().log(
          "%s (%s = %s, %s = %s, %s = %s) took %.2f ms",
          name,
          field1.name(),
          fieldValue1,
          field2.name(),
          fieldValue2,
          field3.name(),
          fieldValue3,
          durationNanos / 1000000.0);
    }

    doRecord(fieldValue1, fieldValue2, fieldValue3, value, unit);
    RequestStateContext.abortIfCancelled();
  }

  private Metadata getMetadata(F1 fieldValue1, F2 fieldValue2, F3 fieldValue3) {
    Metadata.Builder metadataBuilder = Metadata.builder();
    field1.metadataMapper().accept(metadataBuilder, fieldValue1);
    field2.metadataMapper().accept(metadataBuilder, fieldValue2);
    field3.metadataMapper().accept(metadataBuilder, fieldValue3);
    return metadataBuilder.build();
  }

  /** Suppress logging (debug log and performance log) when values are recorded. */
  public final Timer3<F1, F2, F3> suppressLogging() {
    this.suppressLogging = true;
    return this;
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
