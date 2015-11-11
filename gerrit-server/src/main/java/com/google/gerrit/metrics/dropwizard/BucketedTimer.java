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
import com.google.gerrit.metrics.dropwizard.DropWizardMetricMaker.TimerImpl;

import com.codahale.metrics.Metric;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

abstract class BucketedTimer implements BucketedMetric {
  protected final DropWizardMetricMaker metrics;
  protected final String name;
  protected final Field<?>[] fields;
  protected final TimerImpl total;
  protected final Map<Object, TimerImpl> cells;

  BucketedTimer(DropWizardMetricMaker metrics, String name,
      Field<?>... fields) {
    this.metrics = metrics;
    this.name = name;
    this.fields = fields;
    this.total = metrics.newTimerImpl(name + "_total");
    this.cells = new ConcurrentHashMap<>();
  }

  void doRemove() {
    total.remove();
    for (TimerImpl c : cells.values()) {
      c.remove();
    }
    metrics.remove(name);
  }

  TimerImpl forceCreate(Object key) {
    TimerImpl c = cells.get(key);
    if (c != null) {
      return c;
    }

    synchronized (cells) {
      c = cells.get(key);
      if (c == null) {
        c = metrics.newTimerImpl(name + '/' + makeSuffix(key));
        cells.put(key, c);
      }
      return c;
    }
  }

  abstract String makeSuffix(Object key);

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
