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

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.dropwizard.DropWizardMetricMaker.TimerImpl;

import com.codahale.metrics.Metric;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Abstract timer broken down into buckets by {@link Field} values. */
abstract class BucketedTimer implements BucketedMetric {
  private final DropWizardMetricMaker metrics;
  private final String name;
  protected final Field<?>[] fields;
  protected final TimerImpl total;
  private final Map<Object, TimerImpl> cells;

  BucketedTimer(DropWizardMetricMaker metrics, String name, Field<?>... fields) {
    this.metrics = metrics;
    this.name = name;
    this.fields = fields;
    this.total = metrics.newTimerImpl(name + "_total");
    this.cells = new ConcurrentHashMap<>();
  }

  void doRemove() {
    for (TimerImpl c : cells.values()) {
      c.remove();
    }
    total.remove();
    metrics.remove(name);
  }

  TimerImpl forceCreate(Object f1, Object f2) {
    return forceCreate(ImmutableList.of(f1, f2));
  }

  TimerImpl forceCreate(Object f1, Object f2, Object f3) {
    return forceCreate(ImmutableList.of(f1, f2, f3));
  }

  TimerImpl forceCreate(Object key) {
    TimerImpl c = cells.get(key);
    if (c != null) {
      return c;
    }

    synchronized (cells) {
      c = cells.get(key);
      if (c == null) {
        c = metrics.newTimerImpl(name + '/' + name(key));
        cells.put(key, c);
      }
      return c;
    }
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
    return Maps.transformValues(
        cells,
        new Function<TimerImpl, Metric> () {
          @Override
          public Metric apply(TimerImpl in) {
            return in.metric;
          }
        });
  }
}
