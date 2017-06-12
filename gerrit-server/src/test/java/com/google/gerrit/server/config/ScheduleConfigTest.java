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

import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.Config;
import org.joda.time.DateTime;
import org.junit.Test;

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
    assertEquals(ms(7, DAYS) - ms(4, HOURS) - ms(30, MINUTES), initialDelay("05:30", "1w"));

    assertEquals(ms(3, DAYS) + ms(1, HOURS), initialDelay("Mon 11:00", "1w"));
    assertEquals(ms(1, HOURS), initialDelay("Fri 11:00", "1w"));

    assertEquals(ms(1, HOURS), initialDelay("Mon 11:00", "1d"));
    assertEquals(ms(23, HOURS), initialDelay("Mon 09:00", "1d"));
    assertEquals(ms(1, DAYS), initialDelay("Mon 10:00", "1d"));
    assertEquals(ms(1, DAYS), initialDelay("Mon 10:00", "1d"));
  }

  @Test
  public void testCustomKeys() {
    Config rc = new Config();
    rc.setString("a", "b", "i", "1h");
    rc.setString("a", "b", "s", "01:00");

    ScheduleConfig s = new ScheduleConfig(rc, "a", "b", "i", "s", NOW);
    assertEquals(ms(1, HOURS), s.getInterval());
    assertEquals(ms(1, HOURS), s.getInitialDelay());

    s = new ScheduleConfig(rc, "a", "b", "myInterval", "myStart", NOW);
    assertEquals(s.getInterval(), ScheduleConfig.MISSING_CONFIG);
    assertEquals(s.getInitialDelay(), ScheduleConfig.MISSING_CONFIG);
  }

  private static long initialDelay(String startTime, String interval) {
    return new ScheduleConfig(config(startTime, interval), "section", "subsection", NOW)
        .getInitialDelay();
  }

  private static Config config(String startTime, String interval) {
    Config rc = new Config();
    rc.setString("section", "subsection", "startTime", startTime);
    rc.setString("section", "subsection", "interval", interval);
    return rc;
  }

  private static long ms(int cnt, TimeUnit unit) {
    return MILLISECONDS.convert(cnt, unit);
  }
}
