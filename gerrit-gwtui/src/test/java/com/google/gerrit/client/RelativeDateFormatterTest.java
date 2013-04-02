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

import static org.junit.Assert.assertEquals;
import static com.google.gerrit.client.RelativeDateFormatter.YEAR_IN_MILLIS;
import static com.google.gerrit.client.RelativeDateFormatter.SECOND_IN_MILLIS;
import static com.google.gerrit.client.RelativeDateFormatter.MINUTE_IN_MILLIS;
import static com.google.gerrit.client.RelativeDateFormatter.HOUR_IN_MILLIS;
import static com.google.gerrit.client.RelativeDateFormatter.DAY_IN_MILLIS;

import java.util.Date;

import org.eclipse.jgit.util.RelativeDateFormatter;
import org.junit.Test;

public class RelativeDateFormatterTest {

  private static void assertFormat(long ageFromNow, long timeUnit,
      String expectedFormat) {
    Date d = new Date(System.currentTimeMillis() - ageFromNow * timeUnit);
    String s = RelativeDateFormatter.format(d);
    assertEquals(expectedFormat, s);
  }

  @Test
  public void testFuture() {
    assertFormat(-100, YEAR_IN_MILLIS, "in the future");
    assertFormat(-1, SECOND_IN_MILLIS, "in the future");
  }

  @Test
  public void testFormatSeconds() {
    assertFormat(1, SECOND_IN_MILLIS, "1 seconds ago");
    assertFormat(89, SECOND_IN_MILLIS, "89 seconds ago");
  }

  @Test
  public void testFormatMinutes() {
    assertFormat(90, SECOND_IN_MILLIS, "2 minutes ago");
    assertFormat(3, MINUTE_IN_MILLIS, "3 minutes ago");
    assertFormat(60, MINUTE_IN_MILLIS, "60 minutes ago");
    assertFormat(89, MINUTE_IN_MILLIS, "89 minutes ago");
  }

  @Test
  public void testFormatHours() {
    assertFormat(90, MINUTE_IN_MILLIS, "2 hours ago");
    assertFormat(149, MINUTE_IN_MILLIS, "2 hours ago");
    assertFormat(35, HOUR_IN_MILLIS, "35 hours ago");
  }

  @Test
  public void testFormatDays() {
    assertFormat(36, HOUR_IN_MILLIS, "2 days ago");
    assertFormat(13, DAY_IN_MILLIS, "13 days ago");
  }

  @Test
  public void testFormatWeeks() {
    assertFormat(14, DAY_IN_MILLIS, "2 weeks ago");
    assertFormat(69, DAY_IN_MILLIS, "10 weeks ago");
  }

  @Test
  public void testFormatMonths() {
    assertFormat(70, DAY_IN_MILLIS, "2 months ago");
    assertFormat(75, DAY_IN_MILLIS, "3 months ago");
    assertFormat(364, DAY_IN_MILLIS, "12 months ago");
  }

  @Test
  public void testFormatYearsMonths() {
    assertFormat(366, DAY_IN_MILLIS, "1 year ago");
    assertFormat(380, DAY_IN_MILLIS, "1 year, 1 month ago");
    assertFormat(410, DAY_IN_MILLIS, "1 year, 2 months ago");
    assertFormat(2, YEAR_IN_MILLIS, "2 years ago");
    assertFormat(1824, DAY_IN_MILLIS, "4 years, 12 months ago");
  }

  @Test
  public void testFormatYears() {
    assertFormat(5, YEAR_IN_MILLIS, "5 years ago");
    assertFormat(60, YEAR_IN_MILLIS, "60 years ago");
  }
}
