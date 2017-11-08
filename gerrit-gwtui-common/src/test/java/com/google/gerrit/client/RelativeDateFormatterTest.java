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

import static com.google.gerrit.client.RelativeDateFormatter.DAY_IN_MILLIS;
import static com.google.gerrit.client.RelativeDateFormatter.HOUR_IN_MILLIS;
import static com.google.gerrit.client.RelativeDateFormatter.MINUTE_IN_MILLIS;
import static com.google.gerrit.client.RelativeDateFormatter.SECOND_IN_MILLIS;
import static com.google.gerrit.client.RelativeDateFormatter.YEAR_IN_MILLIS;
import static org.junit.Assert.assertEquals;

import java.util.Date;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class RelativeDateFormatterTest {

  @BeforeClass
  public static void setConstants() {
    Constants c = new Constants();
    RelativeDateFormatter.setConstants(c, c);
  }

  @AfterClass
  public static void unsetConstants() {
    RelativeDateFormatter.setConstants(null, null);
  }

  private static void assertFormat(long ageFromNow, long timeUnit, String expectedFormat) {
    Date d = new Date(System.currentTimeMillis() - ageFromNow * timeUnit);
    String s = RelativeDateFormatter.format(d);
    assertEquals(expectedFormat, s);
  }

  @Test
  public void future() {
    assertFormat(-100, YEAR_IN_MILLIS, "in the future");
    assertFormat(-1, SECOND_IN_MILLIS, "in the future");
  }

  @Test
  public void formatSeconds() {
    assertFormat(1, SECOND_IN_MILLIS, "1 second ago");
    assertFormat(89, SECOND_IN_MILLIS, "89 seconds ago");
  }

  @Test
  public void formatMinutes() {
    assertFormat(90, SECOND_IN_MILLIS, "2 minutes ago");
    assertFormat(3, MINUTE_IN_MILLIS, "3 minutes ago");
    assertFormat(60, MINUTE_IN_MILLIS, "60 minutes ago");
    assertFormat(89, MINUTE_IN_MILLIS, "89 minutes ago");
  }

  @Test
  public void formatHours() {
    assertFormat(90, MINUTE_IN_MILLIS, "2 hours ago");
    assertFormat(149, MINUTE_IN_MILLIS, "2 hours ago");
    assertFormat(35, HOUR_IN_MILLIS, "35 hours ago");
  }

  @Test
  public void formatDays() {
    assertFormat(36, HOUR_IN_MILLIS, "2 days ago");
    assertFormat(13, DAY_IN_MILLIS, "13 days ago");
  }

  @Test
  public void formatWeeks() {
    assertFormat(14, DAY_IN_MILLIS, "2 weeks ago");
    assertFormat(69, DAY_IN_MILLIS, "10 weeks ago");
  }

  @Test
  public void formatMonths() {
    assertFormat(70, DAY_IN_MILLIS, "2 months ago");
    assertFormat(75, DAY_IN_MILLIS, "3 months ago");
    assertFormat(364, DAY_IN_MILLIS, "12 months ago");
  }

  @Test
  public void formatYearsMonths() {
    assertFormat(366, DAY_IN_MILLIS, "1 year ago");
    assertFormat(380, DAY_IN_MILLIS, "1 year, 1 month ago");
    assertFormat(410, DAY_IN_MILLIS, "1 year, 2 months ago");
    assertFormat(2, YEAR_IN_MILLIS, "2 years ago");
    assertFormat(1824, DAY_IN_MILLIS, "5 years ago");
    assertFormat(2 * 365 - 10, DAY_IN_MILLIS, "2 years ago");
  }

  @Test
  public void formatYears() {
    assertFormat(5, YEAR_IN_MILLIS, "5 years ago");
    assertFormat(60, YEAR_IN_MILLIS, "60 years ago");
  }

  private static class Constants implements CommonConstants, CommonMessages {
    @Override
    public String inTheFuture() {
      return "in the future";
    }

    @Override
    public String month() {
      return "month";
    }

    @Override
    public String months() {
      return "months";
    }

    @Override
    public String year() {
      return "year";
    }

    @Override
    public String years() {
      return "years";
    }

    @Override
    public String oneSecondAgo() {
      return "1 second ago";
    }

    @Override
    public String oneMinuteAgo() {
      return "1 minute ago";
    }

    @Override
    public String oneHourAgo() {
      return "1 hour ago";
    }

    @Override
    public String oneDayAgo() {
      return "1 day ago";
    }

    @Override
    public String oneWeekAgo() {
      return "1 week ago";
    }

    @Override
    public String oneMonthAgo() {
      return "1 month ago";
    }

    @Override
    public String oneYearAgo() {
      return "1 year ago";
    }

    @Override
    public String secondsAgo(long seconds) {
      return seconds + " seconds ago";
    }

    @Override
    public String minutesAgo(long minutes) {
      return minutes + " minutes ago";
    }

    @Override
    public String hoursAgo(long hours) {
      return hours + " hours ago";
    }

    @Override
    public String daysAgo(long days) {
      return days + " days ago";
    }

    @Override
    public String weeksAgo(long weeks) {
      return weeks + " weeks ago";
    }

    @Override
    public String monthsAgo(long months) {
      return months + " months ago";
    }

    @Override
    public String yearsAgo(long years) {
      return years + " years ago";
    }

    @Override
    public String years0MonthsAgo(long years, String yearLabel) {
      return years + " " + yearLabel + " ago";
    }

    @Override
    public String yearsMonthsAgo(long years, String yearLabel, long months, String monthLabel) {
      return years + " " + yearLabel + ", " + months + " " + monthLabel + " ago";
    }
  }
}
