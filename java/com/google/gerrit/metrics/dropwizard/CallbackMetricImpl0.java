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

import com.codahale.metrics.MetricRegistry;
import com.google.gerrit.metrics.CallbackMetric0;

class CallbackMetricImpl0<V> extends CallbackMetric0<V> implements CallbackMetricGlue {
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
      throw new IllegalArgumentException("unsupported value type " + valueClass.getName());
    }
  }

  private final DropWizardMetricMaker metrics;
  private final MetricRegistry registry;
  private final String name;
  private volatile V value;

  CallbackMetricImpl0(
      DropWizardMetricMaker metrics, MetricRegistry registry, String name, Class<V> valueType) {
    this.metrics = metrics;
    this.registry = registry;
    this.name = name;
    this.value = zeroFor(valueType);
  }

  @Override
  public void beginSet() {}

  @Override
  public void endSet() {}

  @Override
  public void set(V value) {
    this.value = value;
  }

  @Override
  public void remove() {
    metrics.remove(name);
    registry.remove(name);
  }

  @Override
  public void register(Runnable trigger) {
    registry.register(
        name,
        new com.codahale.metrics.Gauge<V>() {
          @Override
          public V getValue() {
            trigger.run();
            return value;
          }
        });
  }
}
