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

package com.google.gerrit.server.config;

import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.joda.time.DateTime;
import org.junit.Test;

import java.text.MessageFormat;
import java.util.concurrent.TimeUnit;

public class ScheduleConfigTest {

  // Friday June 13, 2014 10:00 UTC
  private static final DateTime NOW = DateTime.parse("2014-06-13T10:00:00-00:00");

  @Test
  public void testInitialDelay() throws Exception {
    assertEquals(ms(1, HOURS), initialDelay("11:00", "1h"));
    assertEquals(ms(30, MINUTES), initialDelay("05:30", "1h"));
    assertEquals(ms(30, MINUTES), initialDelay("09:30", "1h"));
    assertEquals(ms(30, MINUTES), initialDelay("13:30", "1h"));
    assertEquals(ms(59, MINUTES), initialDelay("13:59", "1h"));

    assertEquals(ms(1, HOURS), initialDelay("11:00", "1d"));
    assertEquals(ms(19, HOURS) + ms(30, MINUTES), initialDelay("05:30", "1d"));

    assertEquals(ms(1, HOURS), initialDelay("11:00", "1w"));
    assertEquals(ms(7, DAYS) - ms(4, HOURS) - ms(30, MINUTES),
        initialDelay("05:30", "1w"));

    assertEquals(ms(3, DAYS) + ms(1, HOURS), initialDelay("Mon 11:00", "1w"));
    assertEquals(ms(1, HOURS), initialDelay("Fri 11:00", "1w"));

    assertEquals(ms(1, HOURS), initialDelay("Mon 11:00", "1d"));
    assertEquals(ms(23, HOURS), initialDelay("Mon 09:00", "1d"));
    assertEquals(ms(1, DAYS), initialDelay("Mon 10:00", "1d"));
    assertEquals(ms(1, DAYS), initialDelay("Mon 10:00", "1d"));
  }

  @Test
  public void testCustomKeys() throws ConfigInvalidException {
    Config rc = readConfig(MessageFormat.format(
            "[section \"subsection\"]\n{0} = {1}\n{2} = {3}\n",
            "myStartTime", "01:00", "myInterval", "1h"));

    ScheduleConfig scheduleConfig;

    scheduleConfig = new ScheduleConfig(rc, "section",
        "subsection", "myInterval", "myStartTime");
    assertNotEquals(scheduleConfig.getInterval(), ScheduleConfig.MISSING_CONFIG);
    assertNotEquals(scheduleConfig.getInitialDelay(), ScheduleConfig.MISSING_CONFIG);

    scheduleConfig = new ScheduleConfig(rc, "section",
        "subsection", "nonExistent", "myStartTime");
    assertEquals(scheduleConfig.getInterval(), ScheduleConfig.MISSING_CONFIG);
    assertEquals(scheduleConfig.getInitialDelay(), ScheduleConfig.MISSING_CONFIG);
  }

  private static long initialDelay(String startTime, String interval)
      throws ConfigInvalidException {
    return config(startTime, interval).getInitialDelay();
  }

  private static ScheduleConfig config(String startTime, String interval)
      throws ConfigInvalidException {
    Config rc =
        readConfig(MessageFormat.format(
            "[section \"subsection\"]\nstartTime = {0}\ninterval = {1}\n",
            startTime, interval));
    return new ScheduleConfig(rc, "section", "subsection", NOW);
  }

  private static Config readConfig(String dat)
      throws ConfigInvalidException {
    Config config = new Config();
    config.fromText(dat);
    return config;
  }

  private static long ms(int cnt, TimeUnit unit) {
    return MILLISECONDS.convert(cnt, unit);
  }
}
