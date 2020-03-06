// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.util.time;

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.common.UsedAt;
import com.google.gerrit.common.UsedAt.Project;
import com.google.gerrit.server.util.git.DelegateSystemReader;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Calendar;
import java.util.function.LongSupplier;
import org.eclipse.jgit.util.SystemReader;

/** Static utility methods for dealing with dates and times. */
public class TimeUtil {
  private static final LongSupplier SYSTEM_CURRENT_MILLIS_SUPPLIER = System::currentTimeMillis;

  private static volatile LongSupplier currentMillisSupplier = SYSTEM_CURRENT_MILLIS_SUPPLIER;

  public static long nowMs() {
    // We should rather use Instant.now(Clock).toEpochMilli() instead but this would require some
    // changes in our testing code as we wouldn't have clock steps anymore.
    return currentMillisSupplier.getAsLong();
  }

  public static Instant now() {
    return Instant.ofEpochMilli(nowMs());
  }

  public static Timestamp nowTs() {
    return new Timestamp(nowMs());
  }

  /**
   * return the real days of current year
   * @return 365 if a normal year or 366 if a leap year
   */
  public static int daysOfYear() {
    return LocalDate.now().isLeapYear() ? 366 : 365;
  }

  /**
   * Returns the magic timestamp representing no specific time.
   *
   * <p>This "null object" is helpful in contexts where using {@code null} directly is not possible.
   */
  @UsedAt(Project.PLUGIN_CHECKS)
  public static Timestamp never() {
    // Always create a new object as timestamps are mutable.
    return new Timestamp(0);
  }

  public static Timestamp truncateToSecond(Timestamp t) {
    return new Timestamp((t.getTime() / 1000) * 1000);
  }

  @VisibleForTesting
  public static void setCurrentMillisSupplier(LongSupplier customCurrentMillisSupplier) {
    currentMillisSupplier = customCurrentMillisSupplier;

    SystemReader oldSystemReader = SystemReader.getInstance();
    if (!(oldSystemReader instanceof GerritSystemReader)) {
      SystemReader.setInstance(new GerritSystemReader(oldSystemReader));
    }
  }

  @VisibleForTesting
  public static void resetCurrentMillisSupplier() {
    currentMillisSupplier = SYSTEM_CURRENT_MILLIS_SUPPLIER;
    SystemReader.setInstance(null);
  }

  static class GerritSystemReader extends DelegateSystemReader {
    GerritSystemReader(SystemReader reader) {
      super(reader);
    }

    @Override
    public long getCurrentTime() {
      return currentMillisSupplier.getAsLong();
    }
  }

  private TimeUtil() {}
}
