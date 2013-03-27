// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.client;

import com.google.gerrit.client.changes.Util;

import java.util.Date;

/**
 * Formatter to format timestamps relative to the current time using time units
 * in the format defined by {@code git log --relative-date}.
 */
public class RelativeDateFormatter {
  final static long SECOND_IN_MILLIS = 1000;

  final static long MINUTE_IN_MILLIS = 60 * SECOND_IN_MILLIS;

  final static long HOUR_IN_MILLIS = 60 * MINUTE_IN_MILLIS;

  final static long DAY_IN_MILLIS = 24 * HOUR_IN_MILLIS;

  final static long WEEK_IN_MILLIS = 7 * DAY_IN_MILLIS;

  final static long MONTH_IN_MILLIS = 30 * DAY_IN_MILLIS;

  final static long YEAR_IN_MILLIS = 365 * DAY_IN_MILLIS;

  /**
   * @param when {@link Date} to format
   * @return age of given {@link Date} compared to now formatted in the same
   *         relative format as returned by {@code git log --relative-date}
   */
  @SuppressWarnings("boxing")
  public static String format(Date when) {
    long ageMillis = (new Date()).getTime() - when.getTime();

    // shouldn't happen in a perfect world
    if (ageMillis < 0) return Util.C.inTheFuture();

    // seconds
    if (ageMillis < upperLimit(MINUTE_IN_MILLIS)) {
      return Util.M.secondsAgo(round(ageMillis, SECOND_IN_MILLIS));
    }

    // minutes
    if (ageMillis < upperLimit(HOUR_IN_MILLIS)) {
      return Util.M.minutesAgo(round(ageMillis, MINUTE_IN_MILLIS));
    }

    // hours
    if (ageMillis < upperLimit(DAY_IN_MILLIS)) {
      return Util.M.hoursAgo(round(ageMillis, HOUR_IN_MILLIS));
    }

    // up to 14 days use days
    if (ageMillis < 14 * DAY_IN_MILLIS) {
      return Util.M.daysAgo(round(ageMillis, DAY_IN_MILLIS));
    }

    // up to 10 weeks use weeks
    if (ageMillis < 10 * WEEK_IN_MILLIS) {
      return Util.M.weeksAgo(round(ageMillis, WEEK_IN_MILLIS));
    }

    // months
    if (ageMillis < YEAR_IN_MILLIS) {
      return Util.M.monthsAgo(round(ageMillis, MONTH_IN_MILLIS));
    }

    // up to 5 years use "year, months" rounded to months
    if (ageMillis < 5 * YEAR_IN_MILLIS) {
      long years = ageMillis / YEAR_IN_MILLIS;
      String yearLabel = (years > 1) ? Util.C.years() : Util.C.year();
      long months = round(ageMillis % YEAR_IN_MILLIS, MONTH_IN_MILLIS);
      String monthLabel =
          (months > 1) ? Util.C.months() : (months == 1 ? Util.C.month() : "");
      if (months == 0) {
        return Util.M.years0MonthsAgo(years, yearLabel);
      } else {
        return Util.M.yearsMonthsAgo(years, yearLabel, months, monthLabel);
      }
    }

    // years
    return Util.M.yearsAgo(round(ageMillis, YEAR_IN_MILLIS));
  }

  private static long upperLimit(long unit) {
    long limit = unit + unit / 2;
    return limit;
  }

  private static long round(long n, long unit) {
    long rounded = (n + unit / 2) / unit;
    return rounded;
  }
}
