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

package com.google.gerrit.metrics.dropwizard;

import com.google.gerrit.metrics.CallbackMetric0;

class CallbackMetricImpl0<V> extends CallbackMetric0<V> {
  @SuppressWarnings("unchecked")
  static <V> V zeroFor(Class<V> valueClass) {
    if (valueClass == Integer.class) {
      return (V) Integer.valueOf(0);
    } else if (valueClass == Long.class) {
      return (V) Long.valueOf(0);
    } else if (valueClass == Double.class) {
      return (V) Double.valueOf(0);
    } else if (valueClass == Float.class) {
      return (V) Float.valueOf(0);
    } else if (valueClass == String.class) {
      return (V) "";
    } else if (valueClass == Boolean.class) {
      return (V) Boolean.FALSE;
    } else {
      throw new IllegalArgumentException("unsupported value type "
          + valueClass.getName());
    }
  }

  final String name;
  private V value;

  CallbackMetricImpl0(String name, Class<V> valueType) {
    this.name = name;
    this.value = zeroFor(valueType);
  }

  @Override
  public void set(V value) {
    this.value = value;
  }

  @Override
  public void remove() {
    // Triggers register and remove the metric.
  }

  com.codahale.metrics.Gauge<V> gauge(final Runnable trigger) {
    return new com.codahale.metrics.Gauge<V>() {
      @Override
      public V getValue() {
        trigger.run();
        return value;
      }
    };
  }
}