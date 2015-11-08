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

  public Description(String helpText) {
    annotations = Maps.newLinkedHashMapWithExpectedSize(4);
    annotations.put(DESCRIPTION, helpText);
  }

  public Description setUnit(String unitName) {
    annotations.put(UNIT, unitName);
    return this;
  }

  public Description setRate() {
    annotations.put(RATE, TRUE_VALUE);
    return this;
  }

  public Description setGauge() {
    annotations.put(GAUGE, TRUE_VALUE);
    return this;
  }

  public Description setCumulative() {
    annotations.put(CUMULATIVE, TRUE_VALUE);
    return this;
  }

  public boolean isRate() {
    return TRUE_VALUE.equals(annotations.get(RATE));
  }

  public boolean isGauge() {
    return TRUE_VALUE.equals(annotations.get(GAUGE));
  }

  public boolean isCumulative() {
    return TRUE_VALUE.equals(annotations.get(CUMULATIVE));
  }

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

  public ImmutableMap<String, String> getAnnotations() {
    return ImmutableMap.copyOf(annotations);
  }

  @Override
  public String toString() {
    return annotations.toString();
  }
}
