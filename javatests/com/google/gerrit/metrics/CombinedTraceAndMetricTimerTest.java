package com.google.gerrit.metrics;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gerrit.server.logging.Metadata;
import com.google.gerrit.server.logging.TraceContext.TraceTimer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class CombinedTraceAndMetricTimerTest {
  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock private MetricMaker metricMaker;
  @Mock private Timer0 metricTimer0;
  @Mock private Timer1<String> metricTimer1;
  @Mock private Timer2<String, String> metricTimer2;
  @Mock private Timer3<String, String, String> metricTimer3;
  @Mock private Timer0.Context metricTimer0Context;
  @Mock private Timer1.Context<String> metricTimer1Context;
  @Mock private Timer2.Context<String, String> metricTimer2Context;
  @Mock private Timer3.Context<String, String, String> metricTimer3Context;

  private final Description description = new Description("test description");
  private final Field<String> field1 = Field.ofString("field_a", (b, v) -> {}).build();
  private final Field<String> field2 = Field.ofString("field_b", (b, v) -> {}).build();
  private final Field<String> field3 = Field.ofString("field_c", (b, v) -> {}).build();

  @Before
  public void setUp() {
    when(metricMaker.newTimer(anyString(), any(Description.class))).thenReturn(metricTimer0);
    when(metricMaker.newTimer(anyString(), any(Description.class), eq(field1)))
        .thenReturn(metricTimer1);
    when(metricMaker.newTimer(anyString(), any(Description.class), eq(field1), eq(field2)))
        .thenReturn(metricTimer2);
    when(metricMaker.newTimer(
            anyString(), any(Description.class), eq(field1), eq(field2), eq(field3)))
        .thenReturn(metricTimer3);

    when(metricTimer0.start()).thenReturn(metricTimer0Context);
    when(metricTimer1.start(anyString())).thenReturn(metricTimer1Context);
    when(metricTimer2.start(anyString(), anyString())).thenReturn(metricTimer2Context);
    when(metricTimer3.start(anyString(), anyString(), anyString())).thenReturn(metricTimer3Context);
  }

  @Test
  public void newCombinedTraceAndMetricTimer0() {
    TraceTimer traceTimer;
    try (CombinedTraceAndMetricTimer timer =
        CombinedTraceAndMetricTimer.newCombinedTraceAndMetricTimer0(
            "op", Metadata.empty(), metricMaker, description)) {
      verify(metricMaker).newTimer(eq("op"), eq(description));
      traceTimer = timer.getTraceTimer();
      assertThat(traceTimer).isNotNull();
      verify(metricTimer0).start();
    }
    verify(metricTimer0Context).close();
    verifyTraceTimerClosed(traceTimer);
  }

  @Test
  public void newCombinedTraceAndMetricTimer1() {
    TraceTimer traceTimer;
    try (CombinedTraceAndMetricTimer timer =
        CombinedTraceAndMetricTimer.newCombinedTraceAndMetricTimer1(
            "op", Metadata.empty(), metricMaker, description, field1, "value1")) {
      traceTimer = timer.getTraceTimer();
      assertThat(traceTimer).isNotNull();
      verify(metricMaker).newTimer(eq("op"), eq(description), eq(field1));
      verify(metricTimer1).start(eq("value1"));
    }
    verify(metricTimer1Context).close();
    verifyTraceTimerClosed(traceTimer);
  }

  @Test
  public void newCombinedTraceAndMetricTimer2() {
    TraceTimer traceTimer;
    try (CombinedTraceAndMetricTimer timer =
        CombinedTraceAndMetricTimer.newCombinedTraceAndMetricTimer2(
            "op", Metadata.empty(), metricMaker, description, field1, field2, "value1", "value2")) {
      traceTimer = timer.getTraceTimer();
      assertThat(traceTimer).isNotNull();
      verify(metricMaker).newTimer(eq("op"), eq(description), eq(field1), eq(field2));
      verify(metricTimer2).start(eq("value1"), eq("value2"));
    }
    verify(metricTimer2Context).close();
    verifyTraceTimerClosed(traceTimer);
  }

  @Test
  public void newCombinedTraceAndMetricTimer3() {
    TraceTimer traceTimer;
    try (CombinedTraceAndMetricTimer timer =
        CombinedTraceAndMetricTimer.newCombinedTraceAndMetricTimer3(
            "op",
            Metadata.empty(),
            metricMaker,
            description,
            field1,
            field2,
            field3,
            "value1",
            "value2",
            "value3")) {
      traceTimer = timer.getTraceTimer();
      assertThat(traceTimer).isNotNull();
      verify(metricMaker).newTimer(eq("op"), eq(description), eq(field1), eq(field2), eq(field3));
      verify(metricTimer3).start(eq("value1"), eq("value2"), eq("value3"));
    }
    verify(metricTimer3Context).close();
    verifyTraceTimerClosed(traceTimer);
  }

  private static void verifyTraceTimerClosed(TraceTimer traceTimer) {
    // The trace timer should be closed by closing CombinedTraceAndMetricTimer. Closing it again
    // should fail.
    assertThrows(IllegalStateException.class, traceTimer::close);
  }
}