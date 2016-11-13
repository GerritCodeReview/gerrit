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

package com.google.gerrit.testutil;

import static com.google.common.base.Preconditions.checkState;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.sql.Timestamp;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.DateTimeUtils.MillisProvider;
import org.joda.time.DateTimeZone;

/** Static utility methods for dealing with dates and times in tests. */
public class TestTimeUtil {
  public static final DateTime START =
      new DateTime(2009, 9, 30, 17, 0, 0, DateTimeZone.forOffsetHours(-4));

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
    clockMs = new AtomicLong(START.getMillis());
    setClockStep(clockStep, clockStepUnit);
  }

  /**
   * Set the clock step used by {@link com.google.gerrit.common.TimeUtil}.
   *
   * @param clockStep amount to increment clock by on each lookup.
   * @param clockStepUnit time unit for {@code clockStep}.
   */
  public static synchronized void setClockStep(long clockStep, TimeUnit clockStepUnit) {
    checkState(clockMs != null, "call resetWithClockStep first");
    clockStepMs = MILLISECONDS.convert(clockStep, clockStepUnit);
    DateTimeUtils.setCurrentMillisProvider(
        new MillisProvider() {
          @Override
          public long getMillis() {
            return clockMs.getAndAdd(clockStepMs);
          }
        });
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
    DateTimeUtils.setCurrentMillisSystem();
  }

  private TestTimeUtil() {}
}
