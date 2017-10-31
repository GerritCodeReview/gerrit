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

import com.codahale.metrics.Metric;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.dropwizard.DropWizardMetricMaker.CounterImpl;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Abstract counter broken down into buckets by {@link Field} values. */
abstract class BucketedCounter implements BucketedMetric {
  private final DropWizardMetricMaker metrics;
  private final String name;
  private final boolean isRate;
  private final Description.FieldOrdering ordering;
  protected final Field<?>[] fields;
  protected final CounterImpl total;
  private final Map<Object, CounterImpl> cells;
  private final Object lock = new Object();

  BucketedCounter(
      DropWizardMetricMaker metrics, String name, Description desc, Field<?>... fields) {
    this.metrics = metrics;
    this.name = name;
    this.isRate = desc.isRate();
    this.ordering = desc.getFieldOrdering();
    this.fields = fields;
    this.total = metrics.newCounterImpl(name + "_total", isRate);
    this.cells = new ConcurrentHashMap<>();
  }

  void doRemove() {
    for (CounterImpl c : cells.values()) {
      c.remove();
    }
    total.remove();
    metrics.remove(name);
  }

  CounterImpl forceCreate(Object f1, Object f2) {
    return forceCreate(ImmutableList.of(f1, f2));
  }

  CounterImpl forceCreate(Object f1, Object f2, Object f3) {
    return forceCreate(ImmutableList.of(f1, f2, f3));
  }

  CounterImpl forceCreate(Object key) {
    CounterImpl c = cells.get(key);
    if (c != null) {
      return c;
    }

    synchronized (lock) {
      c = cells.get(key);
      if (c == null) {
        c = metrics.newCounterImpl(submetric(key), isRate);
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
    return total.metric;
  }

  @Override
  public Field<?>[] getFields() {
    return fields;
  }

  @Override
  public Map<Object, Metric> getCells() {
    return Maps.transformValues(cells, c -> c.metric);
  }
}
