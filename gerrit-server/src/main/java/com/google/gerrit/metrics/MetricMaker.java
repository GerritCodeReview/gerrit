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

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import java.util.Set;

/** Factory to create metrics for monitoring. */
public abstract class MetricMaker {
  /**
   * Metric whose value increments during the life of the process.
   *
   * @param name field name
   * @param desc field description
   * @return counter
   */
  public abstract Counter0 newCounter(String name, Description desc);

  public abstract <F1> Counter1<F1> newCounter(String name, Description desc, Field<F1> field1);

  public abstract <F1, F2> Counter2<F1, F2> newCounter(
      String name, Description desc, Field<F1> field1, Field<F2> field2);

  public abstract <F1, F2, F3> Counter3<F1, F2, F3> newCounter(
      String name, Description desc, Field<F1> field1, Field<F2> field2, Field<F3> field3);

  /**
   * Metric recording time spent on an operation.
   *
   * @param name field name
   * @param desc field description
   * @return timer
   */
  public abstract Timer0 newTimer(String name, Description desc);

  public abstract <F1> Timer1<F1> newTimer(String name, Description desc, Field<F1> field1);

  public abstract <F1, F2> Timer2<F1, F2> newTimer(
      String name, Description desc, Field<F1> field1, Field<F2> field2);

  public abstract <F1, F2, F3> Timer3<F1, F2, F3> newTimer(
      String name, Description desc, Field<F1> field1, Field<F2> field2, Field<F3> field3);

  /**
   * Metric recording statistical distribution of values.
   *
   * @param name field name
   * @param desc field description
   * @return histogram
   */
  public abstract Histogram0 newHistogram(String name, Description desc);

  public abstract <F1> Histogram1<F1> newHistogram(String name, Description desc, Field<F1> field1);

  public abstract <F1, F2> Histogram2<F1, F2> newHistogram(
      String name, Description desc, Field<F1> field1, Field<F2> field2);

  public abstract <F1, F2, F3> Histogram3<F1, F2, F3> newHistogram(
      String name, Description desc, Field<F1> field1, Field<F2> field2, Field<F3> field3);

  /**
   * Constant value that does not change.
   *
   * @param name unique name of the metric.
   * @param value only value of the metric.
   * @param desc description of the metric.
   */
  public <V> void newConstantMetric(String name, final V value, Description desc) {
    desc.setConstant();

    @SuppressWarnings("unchecked")
    Class<V> type = (Class<V>) value.getClass();
    final CallbackMetric0<V> metric = newCallbackMetric(name, type, desc);
    newTrigger(
        metric,
        new Runnable() {
          @Override
          public void run() {
            metric.set(value);
          }
        });
  }

  /**
   * Instantaneous reading of a value.
   *
   * <pre>
   * metricMaker.newCallbackMetric(&quot;memory&quot;,
   *     new Description(&quot;Total bytes of memory used&quot;)
   *        .setGauge()
   *        .setUnit(Units.BYTES),
   *     new Supplier&lt;Long&gt;() {
   *       public Long get() {
   *         return Runtime.getRuntime().totalMemory();
   *       }
   *     });
   * </pre>
   *
   * @param name unique name of the metric.
   * @param valueClass type of value recorded by the metric.
   * @param desc description of the metric.
   * @param trigger function to compute the value of the metric.
   */
  public <V> void newCallbackMetric(
      String name, Class<V> valueClass, Description desc, final Supplier<V> trigger) {
    final CallbackMetric0<V> metric = newCallbackMetric(name, valueClass, desc);
    newTrigger(
        metric,
        new Runnable() {
          @Override
          public void run() {
            metric.set(trigger.get());
          }
        });
  }

  /**
   * Instantaneous reading of a single value.
   *
   * @param name field name
   * @param valueClass field type
   * @param desc field description
   * @return callback
   */
  public abstract <V> CallbackMetric0<V> newCallbackMetric(
      String name, Class<V> valueClass, Description desc);

  public abstract <F1, V> CallbackMetric1<F1, V> newCallbackMetric(
      String name, Class<V> valueClass, Description desc, Field<F1> field1);

  /**
   * Connect logic to populate a previously created {@link CallbackMetric}.
   *
   * @param metric1 previously created callback
   * @param trigger trigger to connect
   * @return registration handle
   */
  public RegistrationHandle newTrigger(CallbackMetric<?> metric1, Runnable trigger) {
    return newTrigger(ImmutableSet.<CallbackMetric<?>>of(metric1), trigger);
  }

  public RegistrationHandle newTrigger(
      CallbackMetric<?> metric1, CallbackMetric<?> metric2, Runnable trigger) {
    return newTrigger(ImmutableSet.of(metric1, metric2), trigger);
  }

  public RegistrationHandle newTrigger(
      CallbackMetric<?> metric1,
      CallbackMetric<?> metric2,
      CallbackMetric<?> metric3,
      Runnable trigger) {
    return newTrigger(ImmutableSet.of(metric1, metric2, metric3), trigger);
  }

  public abstract RegistrationHandle newTrigger(Set<CallbackMetric<?>> metrics, Runnable trigger);
}
