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