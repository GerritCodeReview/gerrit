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

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

class MetricJson {
  String description;
  String unit;
  Boolean constant;
  Boolean rate;
  Boolean gauge;
  Boolean cumulative;

  Long count;
  Object value;

  Double rate_1m;
  Double rate_5m;
  Double rate_15m;
  Double rate_mean;

  Double p50;
  Double p75;
  Double p95;
  Double p98;
  Double p99;
  Double p99_9;

  Double min;
  Double avg;
  Double max;
  Double sum;
  Double std_dev;

  List<FieldJson> fields;
  Map<String, Object> buckets;

  MetricJson(Metric metric, ImmutableMap<String, String> atts, boolean dataOnly) {
    if (!dataOnly) {
      description = atts.get(Description.DESCRIPTION);
      unit = atts.get(Description.UNIT);
      constant = toBool(atts, Description.CONSTANT);
      rate = toBool(atts, Description.RATE);
      gauge = toBool(atts, Description.GAUGE);
      cumulative = toBool(atts, Description.CUMULATIVE);
    }
    init(metric, atts);
  }

  private void init(Metric metric, ImmutableMap<String, String> atts) {
    if (metric instanceof BucketedMetric) {
      BucketedMetric m = (BucketedMetric) metric;
      if (m.getTotal() != null) {
        init(m.getTotal(), atts);
      }

      Field<?>[] fieldList = m.getFields();
      fields = new ArrayList<>(fieldList.length);
      for (Field<?> f : fieldList) {
        fields.add(new FieldJson(f));
      }
      buckets = makeBuckets(fieldList, m.getCells(), atts);

    } else if (metric instanceof Counter) {
      Counter c = (Counter) metric;
      count = c.getCount();

    } else if (metric instanceof Gauge) {
      Gauge<?> g = (Gauge<?>) metric;
      value = g.getValue();

    } else if (metric instanceof Meter) {
      Meter m = (Meter) metric;
      count = m.getCount();
      rate_1m = m.getOneMinuteRate();
      rate_5m = m.getFiveMinuteRate();
      rate_15m = m.getFifteenMinuteRate();

    } else if (metric instanceof Timer) {
      Timer m = (Timer) metric;
      Snapshot s = m.getSnapshot();
      count = m.getCount();
      rate_1m = m.getOneMinuteRate();
      rate_5m = m.getFiveMinuteRate();
      rate_15m = m.getFifteenMinuteRate();

      double div = Description.getTimeUnit(atts.get(Description.UNIT)).toNanos(1);
      p50 = s.getMedian() / div;
      p75 = s.get75thPercentile() / div;
      p95 = s.get95thPercentile() / div;
      p98 = s.get98thPercentile() / div;
      p99 = s.get99thPercentile() / div;
      p99_9 = s.get999thPercentile() / div;

      min = s.getMin() / div;
      max = s.getMax() / div;
      std_dev = s.getStdDev() / div;

    } else if (metric instanceof Histogram) {
      Histogram m = (Histogram) metric;
      Snapshot s = m.getSnapshot();
      count = m.getCount();

      p50 = s.getMedian();
      p75 = s.get75thPercentile();
      p95 = s.get95thPercentile();
      p98 = s.get98thPercentile();
      p99 = s.get99thPercentile();
      p99_9 = s.get999thPercentile();

      min = (double) s.getMin();
      avg = (double) s.getMean();
      max = (double) s.getMax();
      sum = s.getMean() * m.getCount();
      std_dev = s.getStdDev();
    }
  }

  private static Boolean toBool(ImmutableMap<String, String> atts, String key) {
    return Description.TRUE_VALUE.equals(atts.get(key)) ? true : null;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> makeBuckets(
      Field<?>[] fields, Map<?, Metric> metrics, ImmutableMap<String, String> atts) {
    if (fields.length == 1) {
      Function<Object, String> fmt = (Function<Object, String>) fields[0].formatter();
      Map<String, Object> out = new TreeMap<>();
      for (Map.Entry<?, Metric> e : metrics.entrySet()) {
        out.put(fmt.apply(e.getKey()), new MetricJson(e.getValue(), atts, true));
      }
      return out;
    }

    Map<String, Object> out = new TreeMap<>();
    for (Map.Entry<?, Metric> e : metrics.entrySet()) {
      ImmutableList<Object> keys = (ImmutableList<Object>) e.getKey();
      Map<String, Object> dst = out;

      for (int i = 0; i < fields.length - 1; i++) {
        Function<Object, String> fmt = (Function<Object, String>) fields[i].formatter();
        String key = fmt.apply(keys.get(i));
        Map<String, Object> t = (Map<String, Object>) dst.get(key);
        if (t == null) {
          t = new TreeMap<>();
          dst.put(key, t);
        }
        dst = t;
      }

      Function<Object, String> fmt =
          (Function<Object, String>) fields[fields.length - 1].formatter();
      dst.put(fmt.apply(keys.get(fields.length - 1)), new MetricJson(e.getValue(), atts, true));
    }
    return out;
  }

  static class FieldJson {
    String name;
    String type;
    String description;

    FieldJson(Field<?> field) {
      this.name = field.getName();
      this.description = field.getDescription();
      this.type =
          Enum.class.isAssignableFrom(field.getType()) ? field.getType().getSimpleName() : null;
    }
  }
}
