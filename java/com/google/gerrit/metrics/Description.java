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

import com.google.common.base.Strings;
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
  public static final String CONSTANT = "CONSTANT";
  public static final String FIELD_ORDERING = "FIELD_ORDERING";
  public static final String TRUE_VALUE = "1";

  public static class Units {
    public static final String SECONDS = "seconds";
    public static final String MILLISECONDS = "milliseconds";
    public static final String MICROSECONDS = "microseconds";
    public static final String NANOSECONDS = "nanoseconds";

    public static final String BYTES = "bytes";

    private Units() {}
  }

  public enum FieldOrdering {
    /** Default ordering places fields at end of the parent metric name. */
    AT_END,

    /**
     * Splits the metric name by inserting field values before the last '/' in the metric name. For
     * example {@code "plugins/replication/push_latency"} with a {@code Field.ofString("remote")}
     * will create submetrics named {@code "plugins/replication/some-server/push_latency"}.
     */
    PREFIX_FIELDS_BASENAME;
  }

  private final Map<String, String> annotations;

  /**
   * Describe a metric.
   *
   * @param helpText a short one-sentence string explaining the values captured by the metric. This
   *     may be made available to administrators as documentation in the reporting tools.
   */
  public Description(String helpText) {
    annotations = Maps.newLinkedHashMapWithExpectedSize(4);
    annotations.put(DESCRIPTION, helpText);
  }

  /**
   * Set unit used to describe the value.
   *
   * @param unitName name of the unit, e.g. "requests", "seconds", etc.
   * @return this
   */
  public Description setUnit(String unitName) {
    annotations.put(UNIT, unitName);
    return this;
  }

  /**
   * Mark the value as constant for the life of this process. Typically used for software versions,
   * command line arguments, etc. that cannot change without a process restart.
   *
   * @return this
   */
  public Description setConstant() {
    annotations.put(CONSTANT, TRUE_VALUE);
    return this;
  }

  /**
   * Indicates the metric may be usefully interpreted as a count over short periods of time, such as
   * request arrival rate. May only be applied to a {@link Counter0}.
   *
   * @return this
   */
  public Description setRate() {
    annotations.put(RATE, TRUE_VALUE);
    return this;
  }

  /**
   * Instantaneously sampled value that may increase or decrease at a later time. Memory allocated
   * or open network connections are examples of gauges.
   *
   * @return this
   */
  public Description setGauge() {
    annotations.put(GAUGE, TRUE_VALUE);
    return this;
  }

  /**
   * Indicates the metric accumulates over the lifespan of the process. A {@link Counter0} like
   * total requests handled accumulates over the process and should be {@code setCumulative()}.
   *
   * @return this
   */
  public Description setCumulative() {
    annotations.put(CUMULATIVE, TRUE_VALUE);
    return this;
  }

  /**
   * Configure how fields are ordered into submetric names.
   *
   * @param ordering field ordering
   * @return this
   */
  public Description setFieldOrdering(FieldOrdering ordering) {
    annotations.put(FIELD_ORDERING, ordering.name());
    return this;
  }

  /** @return true if the metric value never changes after startup. */
  public boolean isConstant() {
    return TRUE_VALUE.equals(annotations.get(CONSTANT));
  }

  /** @return true if the metric may be interpreted as a rate over time. */
  public boolean isRate() {
    return TRUE_VALUE.equals(annotations.get(RATE));
  }

  /** @return true if the metric is an instantaneous sample. */
  public boolean isGauge() {
    return TRUE_VALUE.equals(annotations.get(GAUGE));
  }

  /** @return true if the metric accumulates over the lifespan of the process. */
  public boolean isCumulative() {
    return TRUE_VALUE.equals(annotations.get(CUMULATIVE));
  }

  /** @return the suggested field ordering. */
  public FieldOrdering getFieldOrdering() {
    String o = annotations.get(FIELD_ORDERING);
    return o != null ? FieldOrdering.valueOf(o) : FieldOrdering.AT_END;
  }

  /**
   * Decode the unit as a unit of time.
   *
   * @return valid time unit.
   * @throws IllegalArgumentException if the unit is not a valid unit of time.
   */
  public TimeUnit getTimeUnit() {
    return getTimeUnit(annotations.get(UNIT));
  }

  private static final ImmutableMap<String, TimeUnit> TIME_UNITS =
      ImmutableMap.of(
          Units.NANOSECONDS, TimeUnit.NANOSECONDS,
          Units.MICROSECONDS, TimeUnit.MICROSECONDS,
          Units.MILLISECONDS, TimeUnit.MILLISECONDS,
          Units.SECONDS, TimeUnit.SECONDS);

  public static TimeUnit getTimeUnit(String unit) {
    if (Strings.isNullOrEmpty(unit)) {
      throw new IllegalArgumentException("no unit configured");
    }
    TimeUnit u = TIME_UNITS.get(unit);
    if (u == null) {
      throw new IllegalArgumentException(String.format("unit %s not TimeUnit", unit));
    }
    return u;
  }

  /** @return immutable copy of all annotations (configurable properties). */
  public ImmutableMap<String, String> getAnnotations() {
    return ImmutableMap.copyOf(annotations);
  }

  @Override
  public String toString() {
    return annotations.toString();
  }
}
