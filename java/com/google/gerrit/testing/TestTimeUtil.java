// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.testing;

import static com.google.common.base.Preconditions.checkState;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.google.gerrit.server.util.time.TimeUtil;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Static utility methods for dealing with dates and times in tests. */
public class TestTimeUtil {
  public static final Instant START =
      LocalDateTime.of(2009, Month.SEPTEMBER, 30, 17, 0, 0)
          .atOffset(ZoneOffset.ofHours(-4))
          .toInstant();

  private static Long clockStepMs;
  private static AtomicLong clockMs;

  /**
   * Reset the clock to a known start point, then set the clock step.
   *
   * <p>The clock is initially set to 2009/09/30 17:00:00 -0400.
   *
   * @param clockStep amount to increment clock by on each lookup.
   * @param clockStepUnit time unit for {@code clockStep}.
   */
  public static synchronized void resetWithClockStep(long clockStep, TimeUnit clockStepUnit) {
    // Set an arbitrary start point so tests are more repeatable.
    clockMs = new AtomicLong(START.toEpochMilli());
    setClockStep(clockStep, clockStepUnit);
  }

  /**
   * Set the clock step used by {@link com.google.gerrit.server.util.time.TimeUtil}.
   *
   * @param clockStep amount to increment clock by on each lookup.
   * @param clockStepUnit time unit for {@code clockStep}.
   */
  public static synchronized void setClockStep(long clockStep, TimeUnit clockStepUnit) {
    checkState(clockMs != null, "call resetWithClockStep first");
    clockStepMs = MILLISECONDS.convert(clockStep, clockStepUnit);
    TimeUtil.setCurrentMillisSupplier(() -> clockMs.getAndAdd(clockStepMs));
  }

  /** {@link AutoCloseable} handle returned by {@link #withClockStep(long, TimeUnit)}. */
  public static class TempClockStep implements AutoCloseable {
    private final long oldClockStepMs;

    private TempClockStep(long clockStep, TimeUnit clockStepUnit) {
      oldClockStepMs = clockStepMs;
      setClockStep(clockStep, clockStepUnit);
    }

    @Override
    public void close() {
      setClockStep(oldClockStepMs, TimeUnit.MILLISECONDS);
    }
  }

  /**
   * Set a clock step only for the scope of a single try-with-resources block.
   *
   * @param clockStep amount to increment clock by on each lookup.
   * @param clockStepUnit time unit for {@code clockStep}.
   * @return {@link AutoCloseable} handle which resets the clock step to its old value on close.
   */
  public static TempClockStep withClockStep(long clockStep, TimeUnit clockStepUnit) {
    return new TempClockStep(clockStep, clockStepUnit);
  }

  /**
   * Freeze the clock to stop moving only for the scope of a single try-with-resources block.
   *
   * @return {@link AutoCloseable} handle which resets the clock step to its old value on close.
   */
  public static TempClockStep freezeClock() {
    return withClockStep(0, TimeUnit.SECONDS);
  }

  /**
   * Set the clock to a specific timestamp.
   *
   * @param ts time to set
   */
  public static synchronized void setClock(Timestamp ts) {
    checkState(clockMs != null, "call resetWithClockStep first");
    clockMs.set(ts.getTime());
  }

  /**
   * Increment the clock once by a given amount.
   *
   * @param clockStep amount to increment clock by.
   * @param clockStepUnit time unit for {@code clockStep}.
   */
  public static synchronized void incrementClock(long clockStep, TimeUnit clockStepUnit) {
    checkState(clockMs != null, "call resetWithClockStep first");
    clockMs.addAndGet(clockStepUnit.toMillis(clockStep));
  }

  /** Reset the clock to use the actual system clock. */
  public static synchronized void useSystemTime() {
    clockMs = null;
    TimeUtil.resetCurrentMillisSupplier();
  }

  private TestTimeUtil() {}
}
