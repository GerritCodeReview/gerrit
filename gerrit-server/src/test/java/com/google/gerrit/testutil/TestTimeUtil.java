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

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.DateTimeUtils.MillisProvider;
import org.joda.time.DateTimeZone;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Static utility methods for dealing with dates and times in tests. */
public class TestTimeUtil {
  private static Long clockStepMs;
  private static AtomicLong clockMs;

  /**
   * Set the current time used by {@link com.google.gerrit.common.TimeUtil}.
   * <p>
   * If {@link #setClockStep(long, TimeUnit)} was not previously called, first
   * calls {@code setClockStep(1, MILLISECONDS)}.
   *
   * @param now the current time for testing.
   */
  public static synchronized void setClock(DateTime now) {
    if (clockStepMs == null) {
      clockStepMs = 1L;
    }
    clockMs = new AtomicLong(now.getMillis());
  }

  /**
   * Set the clock step used by {@link com.google.gerrit.common.TimeUtil}.
   * <p>
   * If {@link #setClock(DateTime)} was not previously called, first sets the
   * clock to 2009/09/30 17:00:00 UTC.
   *
   * @param clockStep amount to increment clock by on each lookup.
   * @param clockStepUnit time unit for {@code clockStep}.
   */
  public static synchronized void setClockStep(
      long clockStep, TimeUnit clockStepUnit) {
    clockStepMs = MILLISECONDS.convert(clockStep, clockStepUnit);
    // Set an arbitrary start point so tests are more repeatable.
    if (clockMs == null) {
      setClock(new DateTime(2009, 9, 30, 17, 0, 0, DateTimeZone.UTC));
    }

    DateTimeUtils.setCurrentMillisProvider(new MillisProvider() {
      @Override
      public long getMillis() {
        return clockMs.getAndAdd(clockStepMs);
      }
    });
  }

  /** Reset the clock to use the actual system clock. */
  public static synchronized void useSystemTime() {
    DateTimeUtils.setCurrentMillisSystem();
  }

  private TestTimeUtil() {
  }
}
