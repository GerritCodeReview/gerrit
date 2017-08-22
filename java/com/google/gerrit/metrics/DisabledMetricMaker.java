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

import com.google.gerrit.extensions.registration.RegistrationHandle;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Exports no metrics, useful for running batch programs. */
public class DisabledMetricMaker extends MetricMaker {
  @Override
  public Counter0 newCounter(String name, Description desc) {
    return new Counter0() {
      @Override
      public void incrementBy(long value) {}

      @Override
      public void remove() {}
    };
  }

  @Override
  public <F1> Counter1<F1> newCounter(String name, Description desc, Field<F1> field1) {
    return new Counter1<F1>() {
      @Override
      public void incrementBy(F1 field1, long value) {}

      @Override
      public void remove() {}
    };
  }

  @Override
  public <F1, F2> Counter2<F1, F2> newCounter(
      String name, Description desc, Field<F1> field1, Field<F2> field2) {
    return new Counter2<F1, F2>() {
      @Override
      public void incrementBy(F1 field1, F2 field2, long value) {}

      @Override
      public void remove() {}
    };
  }

  @Override
  public <F1, F2, F3> Counter3<F1, F2, F3> newCounter(
      String name, Description desc, Field<F1> field1, Field<F2> field2, Field<F3> field3) {
    return new Counter3<F1, F2, F3>() {
      @Override
      public void incrementBy(F1 field1, F2 field2, F3 field3, long value) {}

      @Override
      public void remove() {}
    };
  }

  @Override
  public Timer0 newTimer(String name, Description desc) {
    return new Timer0() {
      @Override
      public void record(long value, TimeUnit unit) {}

      @Override
      public void remove() {}
    };
  }

  @Override
  public <F1> Timer1<F1> newTimer(String name, Description desc, Field<F1> field1) {
    return new Timer1<F1>() {
      @Override
      public void record(F1 field1, long value, TimeUnit unit) {}

      @Override
      public void remove() {}
    };
  }

  @Override
  public <F1, F2> Timer2<F1, F2> newTimer(
      String name, Description desc, Field<F1> field1, Field<F2> field2) {
    return new Timer2<F1, F2>() {
      @Override
      public void record(F1 field1, F2 field2, long value, TimeUnit unit) {}

      @Override
      public void remove() {}
    };
  }

  @Override
  public <F1, F2, F3> Timer3<F1, F2, F3> newTimer(
      String name, Description desc, Field<F1> field1, Field<F2> field2, Field<F3> field3) {
    return new Timer3<F1, F2, F3>() {
      @Override
      public void record(F1 field1, F2 field2, F3 field3, long value, TimeUnit unit) {}

      @Override
      public void remove() {}
    };
  }

  @Override
  public Histogram0 newHistogram(String name, Description desc) {
    return new Histogram0() {
      @Override
      public void record(long value) {}

      @Override
      public void remove() {}
    };
  }

  @Override
  public <F1> Histogram1<F1> newHistogram(String name, Description desc, Field<F1> field1) {
    return new Histogram1<F1>() {
      @Override
      public void record(F1 field1, long value) {}

      @Override
      public void remove() {}
    };
  }

  @Override
  public <F1, F2> Histogram2<F1, F2> newHistogram(
      String name, Description desc, Field<F1> field1, Field<F2> field2) {
    return new Histogram2<F1, F2>() {
      @Override
      public void record(F1 field1, F2 field2, long value) {}

      @Override
      public void remove() {}
    };
  }

  @Override
  public <F1, F2, F3> Histogram3<F1, F2, F3> newHistogram(
      String name, Description desc, Field<F1> field1, Field<F2> field2, Field<F3> field3) {
    return new Histogram3<F1, F2, F3>() {
      @Override
      public void record(F1 field1, F2 field2, F3 field3, long value) {}

      @Override
      public void remove() {}
    };
  }

  @Override
  public <V> CallbackMetric0<V> newCallbackMetric(
      String name, Class<V> valueClass, Description desc) {
    return new CallbackMetric0<V>() {
      @Override
      public void set(V value) {}

      @Override
      public void remove() {}
    };
  }

  @Override
  public <F1, V> CallbackMetric1<F1, V> newCallbackMetric(
      String name, Class<V> valueClass, Description desc, Field<F1> field1) {
    return new CallbackMetric1<F1, V>() {
      @Override
      public void set(F1 field1, V value) {}

      @Override
      public void forceCreate(F1 field1) {}

      @Override
      public void remove() {}
    };
  }

  @Override
  public RegistrationHandle newTrigger(Set<CallbackMetric<?>> metrics, Runnable trigger) {
    return new RegistrationHandle() {
      @Override
      public void remove() {}
    };
  }
}
