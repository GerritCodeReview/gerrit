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

package com.google.gerrit.common;

import com.google.common.annotations.GwtIncompatible;
import java.sql.Timestamp;
import org.joda.time.DateTimeUtils;

/** Static utility methods for dealing with dates and times. */
@GwtIncompatible("Unemulated org.joda.time.DateTimeUtils")
public class TimeUtil {
  public static long nowMs() {
    return DateTimeUtils.currentTimeMillis();
  }

  public static Timestamp nowTs() {
    return new Timestamp(nowMs());
  }

  public static Timestamp roundToSecond(Timestamp t) {
    return new Timestamp((t.getTime() / 1000) * 1000);
  }

  private TimeUtil() {}
}
