package com.google.gerrit.metrics;


import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.server.logging.Metadata;
import com.google.gerrit.server.logging.TraceContext;
import com.google.gerrit.server.logging.TraceContext.TraceTimer;
import java.util.function.Supplier;

/**
 * A timer that logs the time for an operation using a {@link TraceTimer}. In addition, the timer
 * exports the metrics about the operation using a {@link MetricMaker}.
 */
public final class CombinedTraceAndMetricTimer implements AutoCloseable {
  /**
   * Opens a new timer that logs the time for an operation if request tracing is enabled. In
   * addition, the timer exports the metrics about the operation.
   *
   * @param operation the name of operation the is being performed
   * @param metadata metadata
   * @param metricMaker metric maker to create a com.google.gerrit.metrics.Timer0 with.
   * @return the trace timer
   */
  public static CombinedTraceAndMetricTimer newCombinedTraceAndMetricTimer0(
      String operation, Metadata metadata, MetricMaker metricMaker, Description metricDesc) {
    return new CombinedTraceAndMetricTimer(
        requireNonNull(operation, "operation is required"),
        requireNonNull(metadata, "metadata is required"),
        /* createAndStartMetricTimerFn= */ () ->
            metricMaker.newTimer(operation, metricDesc).start());
  }

  /**
   * Opens a new timer that logs the time for an operation if request tracing is enabled. In
   * addition, the timer exports the metrics about the operation.
   *
   * @param operation the name of operation the is being performed
   * @param metadata metadata
   * @param metricMaker metric maker to create a com.google.gerrit.metrics.Timer1 with.
   * @param field1 field to use for the metric timer.
   * @param fieldValue1 value of the field to use for the metric timer.
   * @return the trace timer
   */
  public static <F1> CombinedTraceAndMetricTimer newCombinedTraceAndMetricTimer1(
      String operation,
      Metadata metadata,
      MetricMaker metricMaker,
      Description metricDesc,
      Field<F1> field1,
      F1 fieldValue1) {
    return new CombinedTraceAndMetricTimer(
        requireNonNull(operation, "operation is required"),
        requireNonNull(metadata, "metadata is required"),
        /* createAndStartMetricTimerFn= */ () ->
            metricMaker.newTimer(operation, metricDesc, field1).start(fieldValue1));
  }

  /**
   * Opens a new timer that logs the time for an operation if request tracing is enabled. In
   * addition, the timer exports the metrics about the operation.
   *
   * @param operation the name of operation the is being performed
   * @param metadata metadata
   * @param metricMaker metric maker to create a com.google.gerrit.metrics.Timer2 with.
   * @param field1 field to use for the metric timer.
   * @param field2 field to use for the metric timer.
   * @param fieldValue1 value of the field to use for the metric timer.
   * @param fieldValue2 value of the field to use for the metric timer.
   * @return the trace timer
   */
  public static <F1, F2> CombinedTraceAndMetricTimer newCombinedTraceAndMetricTimer2(
      String operation,
      Metadata metadata,
      MetricMaker metricMaker,
      Description metricDesc,
      Field<F1> field1,
      Field<F2> field2,
      F1 fieldValue1,
      F2 fieldValue2) {
    return new CombinedTraceAndMetricTimer(
        requireNonNull(operation, "operation is required"),
        requireNonNull(metadata, "metadata is required"),
        /* createAndStartMetricTimerFn= */ () ->
            metricMaker
                .newTimer(operation, metricDesc, field1, field2)
                .start(fieldValue1, fieldValue2));
  }

  /**
   * Opens a new timer that logs the time for an operation if request tracing is enabled. In
   * addition, the timer exports the metrics about the operation.
   *
   * @param operation the name of operation the is being performed
   * @param metadata metadata
   * @param metricMaker metric maker to create a com.google.gerrit.metrics.Timer3 with.
   * @param field1 field to use for the metric timer.
   * @param field2 field to use for the metric timer.
   * @param field3 field to use for the metric timer.
   * @param fieldValue1 value of the field to use for the metric timer.
   * @param fieldValue2 value of the field to use for the metric timer.
   * @param fieldValue3 value of the field to use for the metric timer.
   * @return the trace timer
   */
  public static <F1, F2, F3> CombinedTraceAndMetricTimer newCombinedTraceAndMetricTimer3(
      String operation,
      Metadata metadata,
      MetricMaker metricMaker,
      Description metricDesc,
      Field<F1> field1,
      Field<F2> field2,
      Field<F3> field3,
      F1 fieldValue1,
      F2 fieldValue2,
      F3 fieldValue3) {
    return new CombinedTraceAndMetricTimer(
        requireNonNull(operation, "operation is required"),
        requireNonNull(metadata, "metadata is required"),
        /* createAndStartMetricTimerFn= */ () ->
            metricMaker
                .newTimer(operation, metricDesc, field1, field2, field3)
                .start(fieldValue1, fieldValue2, fieldValue3));
  }

  private final TraceTimer traceTimer;
  private final TimerContext metricTimer;

  private CombinedTraceAndMetricTimer(
      String operation, Metadata metadata, Supplier<TimerContext> createAndStartMetricTimerFn) {
    traceTimer = TraceContext.newTimer(operation, metadata);
    metricTimer = createAndStartMetricTimerFn.get();
  }

  @Override
  public void close() {
    traceTimer.close();
    metricTimer.close();
  }

  @VisibleForTesting
  TraceTimer getTraceTimer() {
    return traceTimer;
  }
}