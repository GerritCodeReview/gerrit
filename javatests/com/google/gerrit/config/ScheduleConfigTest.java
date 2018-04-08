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

package com.google.gerrit.config;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;
import com.google.gerrit.config.ScheduleConfig;
import com.google.gerrit.config.ScheduleConfig.Schedule;

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
  public void defaultKeysWithoutSubsection() {
    Config rc = new Config();
    rc.setString("a", null, ScheduleConfig.KEY_INTERVAL, "1h");
    rc.setString("a", null, ScheduleConfig.KEY_STARTTIME, "01:00");

    assertThat(ScheduleConfig.builder(rc, "a").setNow(NOW).buildSchedule())
        .hasValue(Schedule.create(ms(1, HOURS), ms(1, HOURS)));
  }

  @Test
  public void defaultKeysWithSubsection() {
    Config rc = new Config();
    rc.setString("a", "b", ScheduleConfig.KEY_INTERVAL, "1h");
    rc.setString("a", "b", ScheduleConfig.KEY_STARTTIME, "01:00");

    assertThat(ScheduleConfig.builder(rc, "a").setSubsection("b").setNow(NOW).buildSchedule())
        .hasValue(Schedule.create(ms(1, HOURS), ms(1, HOURS)));
  }

  @Test
  public void customKeysWithoutSubsection() {
    Config rc = new Config();
    rc.setString("a", null, "i", "1h");
    rc.setString("a", null, "s", "01:00");

    assertThat(
            ScheduleConfig.builder(rc, "a")
                .setKeyInterval("i")
                .setKeyStartTime("s")
                .setNow(NOW)
                .buildSchedule())
        .hasValue(Schedule.create(ms(1, HOURS), ms(1, HOURS)));
  }

  @Test
  public void customKeysWithSubsection() {
    Config rc = new Config();
    rc.setString("a", "b", "i", "1h");
    rc.setString("a", "b", "s", "01:00");

    assertThat(
            ScheduleConfig.builder(rc, "a")
                .setSubsection("b")
                .setKeyInterval("i")
                .setKeyStartTime("s")
                .setNow(NOW)
                .buildSchedule())
        .hasValue(Schedule.create(ms(1, HOURS), ms(1, HOURS)));
  }

  @Test
  public void missingConfigWithoutSubsection() {
    Config rc = new Config();
    rc.setString("a", null, ScheduleConfig.KEY_INTERVAL, "1h");
    rc.setString("a", null, ScheduleConfig.KEY_STARTTIME, "01:00");

    assertThat(
            ScheduleConfig.builder(rc, "a")
                .setKeyInterval("myInterval")
                .setKeyStartTime("myStart")
                .buildSchedule())
        .isEmpty();

    assertThat(ScheduleConfig.builder(rc, "x").buildSchedule()).isEmpty();
  }

  @Test
  public void missingConfigWithSubsection() {
    Config rc = new Config();
    rc.setString("a", "b", ScheduleConfig.KEY_INTERVAL, "1h");
    rc.setString("a", "b", ScheduleConfig.KEY_STARTTIME, "01:00");

    assertThat(
            ScheduleConfig.builder(rc, "a")
                .setSubsection("b")
                .setKeyInterval("myInterval")
                .setKeyStartTime("myStart")
                .buildSchedule())
        .isEmpty();

    assertThat(ScheduleConfig.builder(rc, "a").setSubsection("x").buildSchedule()).isEmpty();

    assertThat(ScheduleConfig.builder(rc, "x").setSubsection("b").buildSchedule()).isEmpty();
  }

  @Test
  public void incompleteConfigMissingInterval() {
    Config rc = new Config();
    rc.setString("a", null, ScheduleConfig.KEY_STARTTIME, "01:00");

    assertThat(ScheduleConfig.builder(rc, "a").buildSchedule()).isEmpty();
  }

  @Test
  public void incompleteConfigMissingStartTime() {
    Config rc = new Config();
    rc.setString("a", null, ScheduleConfig.KEY_INTERVAL, "1h");

    assertThat(ScheduleConfig.builder(rc, "a").buildSchedule()).isEmpty();
  }

  @Test
  public void invalidConfigBadInterval() {
    Config rc = new Config();
    rc.setString("a", null, ScheduleConfig.KEY_STARTTIME, "01:00");

    rc.setString("a", null, ScheduleConfig.KEY_INTERVAL, "x");
    assertThat(ScheduleConfig.builder(rc, "a").buildSchedule()).isEmpty();

    rc.setString("a", null, ScheduleConfig.KEY_INTERVAL, "1x");
    assertThat(ScheduleConfig.builder(rc, "a").buildSchedule()).isEmpty();

    rc.setString("a", null, ScheduleConfig.KEY_INTERVAL, "0");
    assertThat(ScheduleConfig.builder(rc, "a").buildSchedule()).isEmpty();

    rc.setString("a", null, ScheduleConfig.KEY_INTERVAL, "-1");
    assertThat(ScheduleConfig.builder(rc, "a").buildSchedule()).isEmpty();
  }

  @Test
  public void invalidConfigBadStartTime() {
    Config rc = new Config();
    rc.setString("a", null, ScheduleConfig.KEY_INTERVAL, "1h");

    rc.setString("a", null, ScheduleConfig.KEY_STARTTIME, "x");
    assertThat(ScheduleConfig.builder(rc, "a").buildSchedule()).isEmpty();

    rc.setString("a", null, ScheduleConfig.KEY_STARTTIME, "Foo 01:00");
    assertThat(ScheduleConfig.builder(rc, "a").buildSchedule()).isEmpty();

    rc.setString("a", null, ScheduleConfig.KEY_STARTTIME, "Mon 01:000");
    assertThat(ScheduleConfig.builder(rc, "a").buildSchedule()).isEmpty();

    rc.setString("a", null, ScheduleConfig.KEY_STARTTIME, "001:00");
    assertThat(ScheduleConfig.builder(rc, "a").buildSchedule()).isEmpty();

    rc.setString("a", null, ScheduleConfig.KEY_STARTTIME, "0100");
    assertThat(ScheduleConfig.builder(rc, "a").buildSchedule()).isEmpty();
  }

  @Test
  public void createInvalidSchedule() {
    assertThat(Schedule.create(-1, "00:00")).isEmpty();
    assertThat(Schedule.create(1, "x")).isEmpty();
    assertThat(Schedule.create(1, "Foo 00:00")).isEmpty();
    assertThat(Schedule.create(0, "Mon 00:000")).isEmpty();
    assertThat(Schedule.create(1, "000:00")).isEmpty();
    assertThat(Schedule.create(1, "0000")).isEmpty();
  }

  private static long initialDelay(String startTime, String interval) {
    Optional<Schedule> schedule =
        ScheduleConfig.builder(config(startTime, interval), "section")
            .setSubsection("subsection")
            .setNow(NOW)
            .buildSchedule();
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
