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

import static java.time.ZoneId.systemDefault;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.common.Nullable;
import java.text.MessageFormat;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@AutoValue
public abstract class ScheduleConfig {
  private static final Logger log = LoggerFactory.getLogger(ScheduleConfig.class);

  private static final long MISSING_CONFIG = -1L;
  private static final long INVALID_CONFIG = -2L;
  private static final String KEY_INTERVAL = "interval";
  private static final String KEY_STARTTIME = "startTime";

  public static ScheduleConfig create(Config config, String section) {
    return builder(config, section).build();
  }

  public static ScheduleConfig create(Config config, String section, String subsection) {
    return builder(config, section, subsection).build();
  }

  public static ScheduleConfig.Builder builder(Config config, String section) {
    return builder(config, section, null);
  }

  public static ScheduleConfig.Builder builder(
      Config config, String section, @Nullable String subsection) {
    return new AutoValue_ScheduleConfig.Builder()
        .setNow(ZonedDateTime.now(systemDefault()))
        .setKeyInterval(KEY_INTERVAL)
        .setKeyStartTime(KEY_STARTTIME)
        .setConfig(config)
        .setSection(section)
        .setSubsection(subsection);
  }

  abstract Config config();

  abstract String section();

  @Nullable
  abstract String subsection();

  abstract String keyInterval();

  abstract String keyStartTime();

  abstract ZonedDateTime now();

  private Optional<Schedule> schedule;

  public Optional<Schedule> schedule() {
    if (schedule == null) {
      schedule = loadSchedule();
    }
    return schedule;
  }

  private Optional<Schedule> loadSchedule() {
    long interval = interval(config(), section(), subsection(), keyInterval());

    long initialDelay;
    if (interval > 0) {
      initialDelay =
          initialDelay(config(), section(), subsection(), keyStartTime(), now(), interval);
    } else {
      initialDelay = interval;
    }

    if (interval == MISSING_CONFIG || initialDelay == MISSING_CONFIG) {
      return Optional.empty();
    }

    return Optional.of(Schedule.create(interval, initialDelay));
  }

  @Override
  public String toString() {
    StringBuilder b = new StringBuilder();
    b.append(formatValue(keyInterval()));
    b.append(", ");
    b.append(formatValue(keyStartTime()));
    return b.toString();
  }

  private String formatValue(String key) {
    StringBuilder b = new StringBuilder();
    b.append(section());
    if (subsection() != null) {
      b.append(".");
      b.append(subsection());
    }
    b.append(".");
    b.append(key);
    String value = config().getString(section(), subsection(), key);
    if (value != null) {
      b.append(" = ");
      b.append(value);
    } else {
      b.append(": NA");
    }
    return b.toString();
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
      ZonedDateTime now,
      long interval) {
    long delay = MISSING_CONFIG;
    String start = rc.getString(section, subsection, keyStartTime);
    try {
      if (start != null) {
        DateTimeFormatter formatter =
            DateTimeFormatter.ofPattern("[E ]HH:mm").withLocale(Locale.US);
        LocalTime firstStartTime = LocalTime.parse(start, formatter);
        ZonedDateTime startTime = now.with(firstStartTime);
        try {
          DayOfWeek dayOfWeek = formatter.parse(start, DayOfWeek::from);
          startTime = startTime.with(dayOfWeek);
        } catch (DateTimeParseException ignored) {
          // Day of week is an optional parameter.
        }
        startTime = startTime.truncatedTo(ChronoUnit.MINUTES);
        delay = Duration.between(now, startTime).toMillis() % interval;
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

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setConfig(Config config);

    public abstract Builder setSection(String section);

    public abstract Builder setSubsection(@Nullable String subsection);

    public abstract Builder setKeyInterval(String keyInterval);

    public abstract Builder setKeyStartTime(String keyStartTime);

    @VisibleForTesting
    abstract Builder setNow(ZonedDateTime now);

    public abstract ScheduleConfig build();
  }

  @AutoValue
  public abstract static class Schedule {
    /** Number of milliseconds between events. */
    public abstract long interval();

    /**
     * Milliseconds between constructor invocation and first event time.
     *
     * <p>If there is any lag between the constructor invocation and queuing the object into an
     * executor the event will run later, as there is no method to adjust for the scheduling delay.
     */
    public abstract long initialDelay();

    static Schedule create(long interval, long initialDelay) {
      return new AutoValue_ScheduleConfig_Schedule(interval, initialDelay);
    }
  }
}
