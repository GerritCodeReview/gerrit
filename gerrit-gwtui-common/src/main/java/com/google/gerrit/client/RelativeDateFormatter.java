// Copyright (C) 2013 The Android Open Source Project
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

import static com.google.gerrit.client.CommonConstants.C;
import static com.google.gerrit.client.CommonMessages.M;

import java.util.Date;

/**
 * Formatter to format timestamps relative to the current time using time units in the format
 * defined by {@code git log --relative-date}.
 */
public class RelativeDateFormatter {
  static final long SECOND_IN_MILLIS = 1000;
  static final long MINUTE_IN_MILLIS = 60 * SECOND_IN_MILLIS;
  static final long HOUR_IN_MILLIS = 60 * MINUTE_IN_MILLIS;
  static final long DAY_IN_MILLIS = 24 * HOUR_IN_MILLIS;
  static final long WEEK_IN_MILLIS = 7 * DAY_IN_MILLIS;
  static final long MONTH_IN_MILLIS = 30 * DAY_IN_MILLIS;
  static final long YEAR_IN_MILLIS = 365 * DAY_IN_MILLIS;

  /**
   * @param when {@link Date} to format
   * @return age of given {@link Date} compared to now formatted in the same relative format as
   *     returned by {@code git log --relative-date}
   */
  @SuppressWarnings("boxing")
  public static String format(Date when) {
    long ageMillis = (new Date()).getTime() - when.getTime();

    // shouldn't happen in a perfect world
    if (ageMillis < 0) {
      return C.inTheFuture();
    }

    // seconds
    if (ageMillis < upperLimit(MINUTE_IN_MILLIS)) {
      long seconds = round(ageMillis, SECOND_IN_MILLIS);
      if (seconds == 1) {
        return C.oneSecondAgo();
      }
      return M.secondsAgo(seconds);
    }

    // minutes
    if (ageMillis < upperLimit(HOUR_IN_MILLIS)) {
      long minutes = round(ageMillis, MINUTE_IN_MILLIS);
      if (minutes == 1) {
        return C.oneMinuteAgo();
      }
      return M.minutesAgo(minutes);
    }

    // hours
    if (ageMillis < upperLimit(DAY_IN_MILLIS)) {
      long hours = round(ageMillis, HOUR_IN_MILLIS);
      if (hours == 1) {
        return C.oneHourAgo();
      }
      return M.hoursAgo(hours);
    }

    // up to 14 days use days
    if (ageMillis < 14 * DAY_IN_MILLIS) {
      long days = round(ageMillis, DAY_IN_MILLIS);
      if (days == 1) {
        return C.oneDayAgo();
      }
      return M.daysAgo(days);
    }

    // up to 10 weeks use weeks
    if (ageMillis < 10 * WEEK_IN_MILLIS) {
      long weeks = round(ageMillis, WEEK_IN_MILLIS);
      if (weeks == 1) {
        return C.oneWeekAgo();
      }
      return M.weeksAgo(weeks);
    }

    // months
    if (ageMillis < YEAR_IN_MILLIS) {
      long months = round(ageMillis, MONTH_IN_MILLIS);
      if (months == 1) {
        return C.oneMonthAgo();
      }
      return M.monthsAgo(months);
    }

    // up to 5 years use "year, months" rounded to months
    if (ageMillis < 5 * YEAR_IN_MILLIS) {
      long years = ageMillis / YEAR_IN_MILLIS;
      String yearLabel = (years > 1) ? C.years() : C.year();
      long months = round(ageMillis % YEAR_IN_MILLIS, MONTH_IN_MILLIS);
      String monthLabel = (months > 1) ? C.months() : (months == 1 ? C.month() : "");
      if (months == 0) {
        return M.years0MonthsAgo(years, yearLabel);
      }
      return M.yearsMonthsAgo(years, yearLabel, months, monthLabel);
    }

    // years
    long years = round(ageMillis, YEAR_IN_MILLIS);
    if (years == 1) {
      return C.oneYearAgo();
    }
    return M.yearsAgo(years);
  }

  private static long upperLimit(long unit) {
    return unit + unit / 2;
  }

  private static long round(long n, long unit) {
    return (n + unit / 2) / unit;
  }
}
