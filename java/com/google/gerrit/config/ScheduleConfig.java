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

import static com.google.common.base.Preconditions.checkNotNull;
import static java.time.ZoneId.systemDefault;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.common.Nullable;
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

/**
 * This class reads a schedule for running a periodic background job from a Git config.
 *
 * <p>A schedule configuration consists of two parameters:
 *
 * <ul>
 *   <li>{@code interval}: Interval for running the periodic background job. The interval must be
 *       larger than zero. The following suffixes are supported to define the time unit for the
 *       interval:
 *       <ul>
 *         <li>{@code s}, {@code sec}, {@code second}, {@code seconds}
 *         <li>{@code m}, {@code min}, {@code minute}, {@code minutes}
 *         <li>{@code h}, {@code hr}, {@code hour}, {@code hours}
 *         <li>{@code d}, {@code day}, {@code days}
 *         <li>{@code w}, {@code week}, {@code weeks} ({@code 1 week} is treated as {@code 7 days})
 *         <li>{@code mon}, {@code month}, {@code months} ({@code 1 month} is treated as {@code 30
 *             days})
 *         <li>{@code y}, {@code year}, {@code years} ({@code 1 year} is treated as {@code 365
 *             days})
 *       </ul>
 *   <li>{@code startTime}: The start time defines the first execution of the periodic background
 *       job. If the configured {@code interval} is shorter than {@code startTime - now} the start
 *       time will be preponed by the maximum integral multiple of {@code interval} so that the
 *       start time is still in the future. {@code startTime} must have one of the following
 *       formats:
 *       <ul>
 *         <li>{@code <day of week> <hours>:<minutes>}
 *         <li>{@code <hours>:<minutes>}
 *       </ul>
 *       The placeholders can have the following values:
 *       <ul>
 *         <li>{@code <day of week>}: {@code Mon}, {@code Tue}, {@code Wed}, {@code Thu}, {@code
 *             Fri}, {@code Sat}, {@code Sun}
 *         <li>{@code <hours>}: {@code 00}-{@code 23}
 *         <li>{@code <minutes>}: {@code 00}-{@code 59}
 *       </ul>
 *       The timezone cannot be specified but is always the system default time-zone.
 * </ul>
 *
 * <p>The section and the subsection from which the {@code interval} and {@code startTime}
 * parameters are read can be configured.
 *
 * <p>Examples for a schedule configuration:
 *
 * <ul>
 *   <li>
 *       <pre>
 * foo.startTime = Fri 10:30
 * foo.interval  = 2 day
 * </pre>
 *       Assuming that the server is started on {@code Mon 7:00} then {@code startTime - now} is
 *       {@code 4 days 3:30 hours}. This is larger than the interval hence the start time is
 *       preponed by the maximum integral multiple of the interval so that start time is still in
 *       the future, i.e. preponed by 4 days. This yields a start time of {@code Mon 10:30}, next
 *       executions are {@code Wed 10:30}, {@code Fri 10:30}. etc.
 *   <li>
 *       <pre>
 * foo.startTime = 6:00
 * foo.interval = 1 day
 * </pre>
 *       Assuming that the server is started on {@code Mon 7:00} then this yields the first run on
 *       next Tuesday at 6:00 and a repetition interval of 1 day.
 * </ul>
 */
@AutoValue
public abstract class ScheduleConfig {
  private static final Logger log = LoggerFactory.getLogger(ScheduleConfig.class);

  @VisibleForTesting static final String KEY_INTERVAL = "interval";
  @VisibleForTesting static final String KEY_STARTTIME = "startTime";

  private static final long MISSING_CONFIG = -1L;
  private static final long INVALID_CONFIG = -2L;

  public static Optional<Schedule> createSchedule(Config config, String section) {
    return builder(config, section).buildSchedule();
  }

  public static ScheduleConfig.Builder builder(Config config, String section) {
    return new AutoValue_ScheduleConfig.Builder()
        .setNow(computeNow())
        .setKeyInterval(KEY_INTERVAL)
        .setKeyStartTime(KEY_STARTTIME)
        .setConfig(config)
        .setSection(section);
  }

  abstract Config config();

  abstract String section();

  @Nullable
  abstract String subsection();

  abstract String keyInterval();

  abstract String keyStartTime();

  abstract ZonedDateTime now();

  @Memoized
  public Optional<Schedule> schedule() {
    long interval = computeInterval(config(), section(), subsection(), keyInterval());

    long initialDelay;
    if (interval > 0) {
      initialDelay =
          computeInitialDelay(config(), section(), subsection(), keyStartTime(), now(), interval);
    } else {
      initialDelay = interval;
    }

    if (isInvalidOrMissing(interval, initialDelay)) {
      return Optional.empty();
    }

    return Optional.of(Schedule.create(interval, initialDelay));
  }

  private boolean isInvalidOrMissing(long interval, long initialDelay) {
    String key = section() + (subsection() != null ? "." + subsection() : "");
    if (interval == MISSING_CONFIG && initialDelay == MISSING_CONFIG) {
      log.info("No schedule configuration for \"{}\".", key);
      return true;
    }

    if (interval == MISSING_CONFIG) {
      log.error(
          "Incomplete schedule configuration for \"{}\" is ignored. Missing value for \"{}\".",
          key,
          key + "." + keyInterval());
      return true;
    }

    if (initialDelay == MISSING_CONFIG) {
      log.error(
          "Incomplete schedule configuration for \"{}\" is ignored. Missing value for \"{}\".",
          key,
          key + "." + keyStartTime());
      return true;
    }

    if (interval <= 0 || initialDelay < 0) {
      log.error("Invalid schedule configuration for \"{}\" is ignored. ", key);
      return true;
    }

    return false;
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

  private static long computeInterval(
      Config rc, String section, String subsection, String keyInterval) {
    try {
      return ConfigUtil.getTimeUnit(
          rc, section, subsection, keyInterval, MISSING_CONFIG, TimeUnit.MILLISECONDS);
    } catch (IllegalArgumentException e) {
      return INVALID_CONFIG;
    }
  }

  private static long computeInitialDelay(
      Config rc,
      String section,
      String subsection,
      String keyStartTime,
      ZonedDateTime now,
      long interval) {
    String start = rc.getString(section, subsection, keyStartTime);
    if (start == null) {
      return MISSING_CONFIG;
    }
    return computeInitialDelay(interval, start, now);
  }

  private static long computeInitialDelay(long interval, String start) {
    return computeInitialDelay(interval, start, computeNow());
  }

  private static long computeInitialDelay(long interval, String start, ZonedDateTime now) {
    checkNotNull(start);

    try {
      DateTimeFormatter formatter = DateTimeFormatter.ofPattern("[E ]HH:mm").withLocale(Locale.US);
      LocalTime firstStartTime = LocalTime.parse(start, formatter);
      ZonedDateTime startTime = now.with(firstStartTime);
      try {
        DayOfWeek dayOfWeek = formatter.parse(start, DayOfWeek::from);
        startTime = startTime.with(dayOfWeek);
      } catch (DateTimeParseException ignored) {
        // Day of week is an optional parameter.
      }
      startTime = startTime.truncatedTo(ChronoUnit.MINUTES);
      long delay = Duration.between(now, startTime).toMillis() % interval;
      if (delay <= 0) {
        delay += interval;
      }
      return delay;
    } catch (DateTimeParseException e) {
      return INVALID_CONFIG;
    }
  }

  private static ZonedDateTime computeNow() {
    return ZonedDateTime.now(systemDefault());
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

    abstract ScheduleConfig build();

    public Optional<Schedule> buildSchedule() {
      return build().schedule();
    }
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

    /**
     * Creates a schedule.
     *
     * <p>{@link ScheduleConfig} defines details about which values are valid for the {@code
     * interval} and {@code startTime} parameters.
     *
     * @param interval the interval in milliseconds
     * @param startTime the start time as "{@code <day of week> <hours>:<minutes>}" or "{@code
     *     <hours>:<minutes>}"
     * @return the schedule
     * @throws IllegalArgumentException if any of the parameters is invalid
     */
    public static Schedule createOrFail(long interval, String startTime) {
      return create(interval, startTime).orElseThrow(IllegalArgumentException::new);
    }

    /**
     * Creates a schedule.
     *
     * <p>{@link ScheduleConfig} defines details about which values are valid for the {@code
     * interval} and {@code startTime} parameters.
     *
     * @param interval the interval in milliseconds
     * @param startTime the start time as "{@code <day of week> <hours>:<minutes>}" or "{@code
     *     <hours>:<minutes>}"
     * @return the schedule or {@link Optional#empty()} if any of the parameters is invalid
     */
    public static Optional<Schedule> create(long interval, String startTime) {
      long initialDelay = computeInitialDelay(interval, startTime);
      if (interval <= 0 || initialDelay < 0) {
        return Optional.empty();
      }
      return Optional.of(create(interval, initialDelay));
    }

    static Schedule create(long interval, long initialDelay) {
      return new AutoValue_ScheduleConfig_Schedule(interval, initialDelay);
    }
  }
}
