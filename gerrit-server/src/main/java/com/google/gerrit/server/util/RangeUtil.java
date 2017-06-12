// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.util;

import com.google.common.primitives.Ints;
import com.google.gerrit.common.Nullable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RangeUtil {
  private static final Pattern RANGE_PATTERN = Pattern.compile("(>|>=|=|<|<=|)([+-]?\\d+)$");

  private RangeUtil() {}

  public static class Range {
    /** The prefix of the query, before the range component. */
    public final String prefix;

    /** The minimum value specified in the query, inclusive. */
    public final int min;

    /** The maximum value specified in the query, inclusive. */
    public final int max;

    public Range(String prefix, int min, int max) {
      this.prefix = prefix;
      this.min = min;
      this.max = max;
    }
  }

  /**
   * Determine the range of values being requested in the given query.
   *
   * @param rangeQuery the raw query, e.g. "{@code added:>12345}"
   * @param minValue the minimum possible value for the field, inclusive
   * @param maxValue the maximum possible value for the field, inclusive
   * @return the calculated {@link Range}, or null if the query is invalid
   */
  @Nullable
  public static Range getRange(String rangeQuery, int minValue, int maxValue) {
    Matcher m = RANGE_PATTERN.matcher(rangeQuery);
    String prefix;
    String test;
    Integer queryInt;
    if (m.find()) {
      prefix = rangeQuery.substring(0, m.start());
      test = m.group(1);
      queryInt = value(m.group(2));
      if (queryInt == null) {
        return null;
      }
    } else {
      return null;
    }

    return getRange(prefix, test, queryInt, minValue, maxValue);
  }

  /**
   * Determine the range of values being requested in the given query.
   *
   * @param prefix a prefix string which is copied into the range
   * @param test the test operator, one of &gt;, &gt;=, =, &lt;, or &lt;=
   * @param queryInt the integer being queried
   * @param minValue the minimum possible value for the field, inclusive
   * @param maxValue the maximum possible value for the field, inclusive
   * @return the calculated {@link Range}
   */
  public static Range getRange(
      String prefix, String test, int queryInt, int minValue, int maxValue) {
    int min;
    int max;
    switch (test) {
      case "=":
      default:
        min = max = queryInt;
        break;
      case ">":
        min = Ints.saturatedCast(queryInt + 1L);
        max = maxValue;
        break;
      case ">=":
        min = queryInt;
        max = maxValue;
        break;
      case "<":
        min = minValue;
        max = Ints.saturatedCast(queryInt - 1L);
        break;
      case "<=":
        min = minValue;
        max = queryInt;
        break;
    }

    return new Range(prefix, min, max);
  }

  private static Integer value(String value) {
    if (value.startsWith("+")) {
      value = value.substring(1);
    }
    return Ints.tryParse(value);
  }
}
