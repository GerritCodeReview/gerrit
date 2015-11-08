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

package com.google.gerrit.metrics;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Describes a metric created by {@link MetricMaker}. */
public class Description {
  public static final String DESCRIPTION = "DESCRIPTION";
  public static final String UNIT = "UNIT";
  public static final String CUMULATIVE = "CUMULATIVE";
  public static final String RATE = "RATE";
  public static final String GAUGE = "GAUGE";
  public static final String TRUE_VALUE = "1";

  public static class Units {
    public static final String SECONDS = "seconds";
    public static final String MILLISECONDS = "milliseconds";
    public static final String MICROSECONDS = "microseconds";
    public static final String NANOSECONDS = "nanoseconds";

    public static final String BYTES = "bytes";

    private Units() {
    }
  }

  private final Map<String, String> annotations;

  /**
   * Describe a metric.
   *
   * @param helpText a short one-sentence string explaining the values captured by
   *        the metric. This may be made available to administrators as documentation.
   */
  public Description(String helpText) {
    annotations = Maps.newLinkedHashMapWithExpectedSize(4);
    annotations.put(DESCRIPTION, helpText);
  }

  /** Unit used to describe the value, e.g. "requests", "seconds", etc. */
  public Description setUnit(String unitName) {
    annotations.put(UNIT, unitName);
    return this;
  }

  /**
   * Indicates the metric may be usefully interpreted as a count over short
   * periods of time, such as request arrival rate. May only be applied to a
   * {@link Counter}.
   */
  public Description setRate() {
    annotations.put(RATE, TRUE_VALUE);
    return this;
  }

  /**
   * Instantaneously sampled value that may increase or decrease at a later
   * time. Memory allocated or open network connections are examples of gauges.
   */
  public Description setGauge() {
    annotations.put(GAUGE, TRUE_VALUE);
    return this;
  }

  /**
   * Indicates the metric accumulates over the lifespan of the process. A
   * {@link Counter} like total requests handled accumulates over the process
   * and should be {@code setCumulative()}.
   */
  public Description setCumulative() {
    annotations.put(CUMULATIVE, TRUE_VALUE);
    return this;
  }

  /** True if the metric may be interpreted as a rate over time. */
  public boolean isRate() {
    return TRUE_VALUE.equals(annotations.get(RATE));
  }

  /** True if the metric is an instantaneous sample. */
  public boolean isGauge() {
    return TRUE_VALUE.equals(annotations.get(GAUGE));
  }

  /** True if the metric accumulates over the lifespan of the process. */
  public boolean isCumulative() {
    return TRUE_VALUE.equals(annotations.get(CUMULATIVE));
  }

  /**
   * Decode the unit as a unit of time.
   *
   * @return valid time unit.
   * @throws IllegalStateException if the unit is not a valid unit of time.
   */
  public TimeUnit getTimeUnit() {
    String unit = annotations.get(UNIT);
    if (unit == null) {
      throw new IllegalStateException("no unit configured");
    } else if (Units.NANOSECONDS.equals(unit)) {
      return TimeUnit.NANOSECONDS;
    } else if (Units.MICROSECONDS.equals(unit)) {
      return TimeUnit.MICROSECONDS;
    } else if (Units.MILLISECONDS.equals(unit)) {
      return TimeUnit.MILLISECONDS;
    } else if (Units.SECONDS.equals(unit)) {
      return TimeUnit.SECONDS;
    } else {
      throw new IllegalStateException(String.format(
          "unit %s not TimeUnit", unit));
    }
  }

  /** Immutable copy of all annotations (configurable properties). */
  public ImmutableMap<String, String> getAnnotations() {
    return ImmutableMap.copyOf(annotations);
  }

  @Override
  public String toString() {
    return annotations.toString();
  }
}
