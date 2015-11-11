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
import com.google.common.collect.Maps;
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.Timer1;
import com.google.gerrit.metrics.dropwizard.DropWizardMetricMaker.TimerImpl;

import com.codahale.metrics.Metric;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

class TimerImpl1<F1> extends Timer1<F1> implements BucketedMetric {
  private final DropWizardMetricMaker metrics;
  private final String name;
  private final Field<F1> field1;
  private final TimerImpl total;
  private final Map<F1, TimerImpl> cells;

  TimerImpl1(DropWizardMetricMaker metrics, String name, Field<F1> field1) {
    this.metrics = metrics;
    this.name = name;
    this.total = metrics.newTimerImpl(name + "_total");
    this.field1 = field1;
    this.cells = new ConcurrentHashMap<>();
  }

  @Override
  public void record(F1 field1, long value, TimeUnit unit) {
    total.record(value, unit);
    forceCreate(field1).record(value, unit);
  }

  @Override
  public void remove() {
    total.remove();
    for (TimerImpl c : cells.values()) {
      c.remove();
    }
    metrics.remove(name);
  }

  private TimerImpl forceCreate(F1 field1) {
    TimerImpl cntr = cells.get(field1);
    if (cntr != null) {
      return cntr;
    }

    synchronized (cells) {
      cntr = cells.get(field1);
      if (cntr == null) {
        cntr = make(field1);
        cells.put(field1, cntr);
      }
      return cntr;
    }
  }

  private TimerImpl make(F1 key1) {
    return metrics.newTimerImpl(name + "/" + field1.formatter().apply(key1));
  }

  @Override
  public Metric getTotal() {
    return total.metric;
  }

  @Override
  public Field<?>[] getFields() {
    return new Field[] {field1};
  }

  @Override
  public Map<F1, Metric> getMetrics() {
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
