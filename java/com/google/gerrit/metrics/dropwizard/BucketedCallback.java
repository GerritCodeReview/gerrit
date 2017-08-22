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

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Field;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Abstract callback metric broken down into buckets. */
abstract class BucketedCallback<V> implements BucketedMetric {
  private final DropWizardMetricMaker metrics;
  private final MetricRegistry registry;
  private final String name;
  private final Description.FieldOrdering ordering;
  protected final Field<?>[] fields;
  private final V zero;
  private final Map<Object, ValueGauge> cells;
  protected volatile Runnable trigger;
  private final Object lock = new Object();

  BucketedCallback(
      DropWizardMetricMaker metrics,
      MetricRegistry registry,
      String name,
      Class<V> valueType,
      Description desc,
      Field<?>... fields) {
    this.metrics = metrics;
    this.registry = registry;
    this.name = name;
    this.ordering = desc.getFieldOrdering();
    this.fields = fields;
    this.zero = CallbackMetricImpl0.zeroFor(valueType);
    this.cells = new ConcurrentHashMap<>();
  }

  void doRemove() {
    for (Object key : cells.keySet()) {
      registry.remove(submetric(key));
    }
    metrics.remove(name);
  }

  void doBeginSet() {
    for (ValueGauge g : cells.values()) {
      g.set = false;
    }
  }

  void doPrune() {
    Iterator<Map.Entry<Object, ValueGauge>> i = cells.entrySet().iterator();
    while (i.hasNext()) {
      if (!i.next().getValue().set) {
        i.remove();
      }
    }
  }

  void doEndSet() {
    for (ValueGauge g : cells.values()) {
      if (!g.set) {
        g.value = zero;
      }
    }
  }

  ValueGauge getOrCreate(Object f1, Object f2) {
    return getOrCreate(ImmutableList.of(f1, f2));
  }

  ValueGauge getOrCreate(Object f1, Object f2, Object f3) {
    return getOrCreate(ImmutableList.of(f1, f2, f3));
  }

  ValueGauge getOrCreate(Object key) {
    ValueGauge c = cells.get(key);
    if (c != null) {
      return c;
    }

    synchronized (lock) {
      c = cells.get(key);
      if (c == null) {
        c = new ValueGauge();
        registry.register(submetric(key), c);
        cells.put(key, c);
      }
      return c;
    }
  }

  private String submetric(Object key) {
    return DropWizardMetricMaker.name(ordering, name, name(key));
  }

  abstract String name(Object key);

  @Override
  public Metric getTotal() {
    return null;
  }

  @Override
  public Field<?>[] getFields() {
    return fields;
  }

  @Override
  public Map<Object, Metric> getCells() {
    return Maps.transformValues(cells, in -> (Metric) in);
  }

  final class ValueGauge implements Gauge<V> {
    volatile V value = zero;
    boolean set;

    @Override
    public V getValue() {
      Runnable t = trigger;
      if (t != null) {
        t.run();
      }
      return value;
    }
  }
}
