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

import org.eclipse.jgit.lib.Config;
import org.joda.time.DateTime;
import org.joda.time.LocalDateTime;
import org.joda.time.LocalTime;
import org.joda.time.MutableDateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.MessageFormat;
import java.util.concurrent.TimeUnit;

public class ScheduleConfig {
  private static final Logger log = LoggerFactory
      .getLogger(ScheduleConfig.class);
  public static final long MISSING_CONFIG = -1L;
  public static final long INVALID_CONFIG = -2L;
  private static final String KEY_INTERVAL = "interval";
  private static final String KEY_STARTTIME = "startTime";

  private final long initialDelay;
  private final long interval;

  public ScheduleConfig(Config rc, String section) {
    this(rc, section, null);
  }

  public ScheduleConfig(Config rc, String section, String subsection) {
    this(rc, section, subsection, DateTime.now());
  }

  /* For testing we need to be able to pass now */
  ScheduleConfig(Config rc, String section, String subsection, DateTime now) {
    this.interval = interval(rc, section, subsection);
    this.initialDelay = initialDelay(rc, section, subsection, now, interval);
  }

  public long getInitialDelay() {
    return initialDelay;
  }

  public long getInterval() {
    return interval;
  }

  private static long interval(Config rc, String section, String subsection) {
    long interval = MISSING_CONFIG;
    try {
      interval =
          ConfigUtil.getTimeUnit(rc, section, subsection, KEY_INTERVAL, -1,
              TimeUnit.MILLISECONDS);
      if (interval == MISSING_CONFIG) {
        log.info(MessageFormat.format(
            "{0} schedule parameter \"{0}.{1}\" is not configured", section,
            KEY_INTERVAL));
      }
    } catch (IllegalArgumentException e) {
      log.error(MessageFormat.format(
          "Invalid {0} schedule parameter \"{0}.{1}\"", section, KEY_INTERVAL),
          e);
      interval = INVALID_CONFIG;
    }
    return interval;
  }

  private static long initialDelay(Config rc, String section,
      String subsection, DateTime now, long interval) {
    long delay = MISSING_CONFIG;
    String start = rc.getString(section, subsection, KEY_STARTTIME);
    try {
      if (start != null) {
        DateTimeFormatter formatter = ISODateTimeFormat.hourMinute();
        MutableDateTime startTime = now.toMutableDateTime();
        LocalTime firstStartTime = null;
        LocalDateTime firstStartDateTime = null;
        try {
          firstStartTime = formatter.parseLocalTime(start);
          startTime.hourOfDay().set(firstStartTime.getHourOfDay());
          startTime.minuteOfHour().set(firstStartTime.getMinuteOfHour());
        } catch (IllegalArgumentException e1) {
          formatter = DateTimeFormat.forPattern("E HH:mm");
          firstStartDateTime = formatter.parseLocalDateTime(start);
          startTime.dayOfWeek().set(firstStartDateTime.getDayOfWeek());
          startTime.hourOfDay().set(firstStartDateTime.getHourOfDay());
          startTime.minuteOfHour().set(firstStartDateTime.getMinuteOfHour());
        }
        startTime.secondOfMinute().set(0);
        startTime.millisOfSecond().set(0);
        while (startTime.isBefore(now)) {
          startTime.add(interval);
        }
        while (startTime.getMillis() - now.getMillis() > interval) {
          startTime.add(-interval);
        }
        delay = startTime.getMillis() - now.getMillis();
      } else {
        log.info(MessageFormat.format(
            "{0} schedule parameter \"{0}.{1}\" is not configured", section,
            KEY_STARTTIME));
      }
    } catch (IllegalArgumentException e2) {
      log.error(
          MessageFormat.format("Invalid {0} schedule parameter \"{0}.{1}\"",
              section, KEY_STARTTIME), e2);
      delay = INVALID_CONFIG;
    }
    return delay;
  }

}
