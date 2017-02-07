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

import com.google.common.annotations.VisibleForTesting;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
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

public class ScheduleConfig {
  private static final Logger log = LoggerFactory.getLogger(ScheduleConfig.class);
  public static final long MISSING_CONFIG = -1L;
  public static final long INVALID_CONFIG = -2L;
  private static final String KEY_INTERVAL = "interval";
  private static final String KEY_STARTTIME = "startTime";

  private final Config rc;
  private final String section;
  private final String subsection;
  private final String keyInterval;
  private final String keyStartTime;
  private final long initialDelay;
  private final long interval;

  public ScheduleConfig(Config rc, String section) {
    this(rc, section, null);
  }

  public ScheduleConfig(Config rc, String section, String subsection) {
    this(rc, section, subsection, DateTime.now());
  }

  public ScheduleConfig(
      Config rc, String section, String subsection, String keyInterval, String keyStartTime) {
    this(rc, section, subsection, keyInterval, keyStartTime, DateTime.now());
  }

  @VisibleForTesting
  ScheduleConfig(Config rc, String section, String subsection, DateTime now) {
    this(rc, section, subsection, KEY_INTERVAL, KEY_STARTTIME, now);
  }

  @VisibleForTesting
  ScheduleConfig(
      Config rc,
      String section,
      String subsection,
      String keyInterval,
      String keyStartTime,
      DateTime now) {
    this.rc = rc;
    this.section = section;
    this.subsection = subsection;
    this.keyInterval = keyInterval;
    this.keyStartTime = keyStartTime;
    this.interval = interval(rc, section, subsection, keyInterval);
    if (interval > 0) {
      this.initialDelay = initialDelay(rc, section, subsection, keyStartTime, now, interval);
    } else {
      this.initialDelay = interval;
    }
  }

  /**
   * Milliseconds between constructor invocation and first event time.
   *
   * <p>If there is any lag between the constructor invocation and queuing the object into an
   * executor the event will run later, as there is no method to adjust for the scheduling delay.
   */
  public long getInitialDelay() {
    return initialDelay;
  }

  /** Number of milliseconds between events. */
  public long getInterval() {
    return interval;
  }

  private static long interval(Config rc, String section, String subsection, String keyInterval) {
    long interval = MISSING_CONFIG;
    try {
      interval =
          ConfigUtil.getTimeUnit(rc, section, subsection, keyInterval, -1, TimeUnit.MILLISECONDS);
      if (interval == MISSING_CONFIG) {
        log.info(
            MessageFormat.format(
                "{0} schedule parameter \"{0}.{1}\" is not configured", section, keyInterval));
      }
    } catch (IllegalArgumentException e) {
      log.error(
          MessageFormat.format("Invalid {0} schedule parameter \"{0}.{1}\"", section, keyInterval),
          e);
      interval = INVALID_CONFIG;
    }
    return interval;
  }

  private static long initialDelay(
      Config rc,
      String section,
      String subsection,
      String keyStartTime,
      DateTime now,
      long interval) {
    long delay = MISSING_CONFIG;
    String start = rc.getString(section, subsection, keyStartTime);
    try {
      if (start != null) {
        DateTimeFormatter formatter;
        MutableDateTime startTime = now.toMutableDateTime();
        try {
          formatter = ISODateTimeFormat.hourMinute();
          LocalTime firstStartTime = formatter.parseLocalTime(start);
          startTime.hourOfDay().set(firstStartTime.getHourOfDay());
          startTime.minuteOfHour().set(firstStartTime.getMinuteOfHour());
        } catch (IllegalArgumentException e1) {
          formatter = DateTimeFormat.forPattern("E HH:mm").withLocale(Locale.US);
          LocalDateTime firstStartDateTime = formatter.parseLocalDateTime(start);
          startTime.dayOfWeek().set(firstStartDateTime.getDayOfWeek());
          startTime.hourOfDay().set(firstStartDateTime.getHourOfDay());
          startTime.minuteOfHour().set(firstStartDateTime.getMinuteOfHour());
        }
        startTime.secondOfMinute().set(0);
        startTime.millisOfSecond().set(0);
        long s = startTime.getMillis();
        long n = now.getMillis();
        delay = (s - n) % interval;
        if (delay <= 0) {
          delay += interval;
        }
      } else {
        log.info(
            MessageFormat.format(
                "{0} schedule parameter \"{0}.{1}\" is not configured", section, keyStartTime));
      }
    } catch (IllegalArgumentException e2) {
      log.error(
          MessageFormat.format("Invalid {0} schedule parameter \"{0}.{1}\"", section, keyStartTime),
          e2);
      delay = INVALID_CONFIG;
    }
    return delay;
  }

  @Override
  public String toString() {
    StringBuilder b = new StringBuilder();
    b.append(formatValue(keyInterval));
    b.append(", ");
    b.append(formatValue(keyStartTime));
    return b.toString();
  }

  private String formatValue(String key) {
    StringBuilder b = new StringBuilder();
    b.append(section);
    if (subsection != null) {
      b.append(".");
      b.append(subsection);
    }
    b.append(".");
    b.append(key);
    String value = rc.getString(section, subsection, key);
    if (value != null) {
      b.append(" = ");
      b.append(value);
    } else {
      b.append(": NA");
    }
    return b.toString();
  }
}
