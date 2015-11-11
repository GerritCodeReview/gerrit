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
import com.google.common.base.Joiner;
import com.google.common.collect.Maps;
import com.google.gerrit.metrics.Counter;
import com.google.gerrit.metrics.Counter1;
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.dropwizard.DropWizardMetricMaker.CounterImpl;

import com.codahale.metrics.Metric;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class CounterImpl1<F1> extends Counter1<F1> implements BucketedMetric {
  private final DropWizardMetricMaker metrics;
  private final String name;
  private final boolean isRate;
  private final Field<F1> field1;
  private final CounterImpl total;
  private final Map<F1, CounterImpl> cells;

  CounterImpl1(DropWizardMetricMaker metrics,
      String name, boolean rate, Field<F1> field1) {
    this.metrics = metrics;
    this.name = name;
    this.isRate = rate;
    this.field1 = field1;
    this.total = metrics.newCounterImpl(name + "_total", rate);
    this.cells = new ConcurrentHashMap<>();
  }

  @Override
  public void incrementBy(F1 field1, long value) {
    total.incrementBy(value);
    forceCreate(field1).incrementBy(value);
  }

  @Override
  public void remove() {
    total.remove();
    for (Counter c : cells.values()) {
      c.remove();
    }
    metrics.remove(name);
  }

  private Counter forceCreate(F1 field1) {
    CounterImpl cntr = cells.get(field1);
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

  private CounterImpl make(F1 key1) {
    return metrics.newCounterImpl(Joiner.on('/').join(
        name,
        metrics.encode(field1.formatter().apply(key1))),
        isRate);
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
  public Map<F1, Metric> getCells() {
    return Maps.transformValues(
        cells,
        new Function<CounterImpl, Metric> () {
          @Override
          public Metric apply(CounterImpl in) {
            return in.metric;
          }
        });
  }
}
