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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;

import com.google.gerrit.server.config.ScheduleConfig.Schedule;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;

public class ScheduleConfigTest {

  // Friday June 13, 2014 10:00 UTC
  private static final ZonedDateTime NOW =
      LocalDateTime.of(2014, Month.JUNE, 13, 10, 0, 0).atOffset(ZoneOffset.UTC).toZonedDateTime();

  @Test
  public void initialDelay() throws Exception {
    assertThat(initialDelay("11:00", "1h")).isEqualTo(ms(1, HOURS));
    assertThat(initialDelay("05:30", "1h")).isEqualTo(ms(30, MINUTES));
    assertThat(initialDelay("09:30", "1h")).isEqualTo(ms(30, MINUTES));
    assertThat(initialDelay("13:30", "1h")).isEqualTo(ms(30, MINUTES));
    assertThat(initialDelay("13:59", "1h")).isEqualTo(ms(59, MINUTES));

    assertThat(initialDelay("11:00", "1d")).isEqualTo(ms(1, HOURS));
    assertThat(initialDelay("05:30", "1d")).isEqualTo(ms(19, HOURS) + ms(30, MINUTES));

    assertThat(initialDelay("11:00", "1w")).isEqualTo(ms(1, HOURS));
    assertThat(initialDelay("05:30", "1w")).isEqualTo(ms(7, DAYS) - ms(4, HOURS) - ms(30, MINUTES));

    assertThat(initialDelay("Mon 11:00", "1w")).isEqualTo(ms(3, DAYS) + ms(1, HOURS));
    assertThat(initialDelay("Fri 11:00", "1w")).isEqualTo(ms(1, HOURS));

    assertThat(initialDelay("Mon 11:00", "1d")).isEqualTo(ms(1, HOURS));
    assertThat(initialDelay("Mon 09:00", "1d")).isEqualTo(ms(23, HOURS));
    assertThat(initialDelay("Mon 10:00", "1d")).isEqualTo(ms(1, DAYS));
    assertThat(initialDelay("Mon 10:00", "1d")).isEqualTo(ms(1, DAYS));
  }

  @Test
  public void customKeys() {
    Config rc = new Config();
    rc.setString("a", "b", "i", "1h");
    rc.setString("a", "b", "s", "01:00");

    ScheduleConfig s =
        ScheduleConfig.builder(rc, "a")
            .setSubsection("b")
            .setKeyInterval("i")
            .setKeyStartTime("s")
            .setNow(NOW)
            .build();
    assertThat(s.schedule()).hasValue(Schedule.create(ms(1, HOURS), ms(1, HOURS)));

    s =
        ScheduleConfig.builder(rc, "a")
            .setSubsection("b")
            .setKeyInterval("myInterval")
            .setKeyStartTime("myStart")
            .setNow(NOW)
            .build();
    assertThat(s.schedule()).isEmpty();
  }

  private static long initialDelay(String startTime, String interval) {
    Optional<Schedule> schedule =
        ScheduleConfig.builder(config(startTime, interval), "section")
            .setSubsection("subsection")
            .setNow(NOW)
            .build()
            .schedule();
    assertThat(schedule).isPresent();
    return schedule.get().initialDelay();
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
