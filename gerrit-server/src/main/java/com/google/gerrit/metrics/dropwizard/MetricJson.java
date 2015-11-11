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
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Field;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

class MetricJson {
  String description;
  String unit;
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

  Long min;
  Long max;
  Double std_dev;

  List<FieldJson> fields;
  Map<String, MetricJson> buckets;

  MetricJson(Metric metric, ImmutableMap<String, String> atts) {
    init(metric);
    description = atts.get(Description.DESCRIPTION);
    unit = atts.get(Description.UNIT);
    rate = toBool(atts, Description.RATE);
    gauge = toBool(atts, Description.GAUGE);
    cumulative = toBool(atts, Description.CUMULATIVE);
  }

  MetricJson(Metric metric) {
    init(metric);
  }

  private void init(Metric metric) {
    if (metric instanceof BucketedMetric) {
      BucketedMetric m = (BucketedMetric) metric;
      if (m.getTotal() != null) {
        init(m.getTotal());
      }

      Field<?>[] fieldList = m.getFields();
      fields = new ArrayList<>(fieldList.length);
      for (Field<?> f : fieldList) {
        fields.add(new FieldJson(f));
      }
      buckets = makeBuckets(fieldList[0], m.getMetrics());

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

      p50 = s.getMedian();
      p75 = s.get75thPercentile();
      p95 = s.get95thPercentile();
      p98 = s.get98thPercentile();
      p99 = s.get99thPercentile();
      p99_9 = s.get999thPercentile();

      min = s.getMin();
      max = s.getMax();
      std_dev = s.getStdDev();
    }
  }

  private static Boolean toBool(ImmutableMap<String, String> atts, String key) {
    return Description.TRUE_VALUE.equals(atts.get(key)) ? true : null;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, MetricJson> makeBuckets(
      Field<?> field,
      Map<?, Metric> metrics) {
    return makeBucketsHelper(
        (Field<Object>) field,
        (Map<Object, Metric>) metrics);
  }

  private static <F1> Map<String, MetricJson> makeBucketsHelper(
      Field<F1> field,
      Map<F1, Metric> metrics) {
    Function<F1, String> fmt = field.formatter();
    Map<String, MetricJson> out = new TreeMap<>();
    for (Map.Entry<F1, Metric> e : metrics.entrySet()) {
      out.put(
          fmt.apply(e.getKey()),
          new MetricJson(e.getValue()));
    }
    return out;
  }

  static class FieldJson {
    String name;
    String type;

    FieldJson(Field<?> field) {
      this.name = field.getName();
      this.type = Enum.class.isAssignableFrom(field.getType())
          ? field.getType().getSimpleName()
          : null;
    }
  }
}
