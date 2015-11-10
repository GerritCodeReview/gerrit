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

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.metrics.Description;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;

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

  MetricJson(Metric metric, ImmutableMap<String, String> atts) {
    this(metric);
    description = atts.get(Description.DESCRIPTION);
    unit = atts.get(Description.UNIT);
    rate = toBool(atts, Description.RATE);
    gauge = toBool(atts, Description.GAUGE);
    cumulative = toBool(atts, Description.CUMULATIVE);
  }

  MetricJson(Metric metric) {
    if (metric instanceof Counter) {
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
}
