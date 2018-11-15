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

package com.google.gerrit.client;

import com.google.gerrit.client.info.GeneralPreferences;
import com.google.gwt.i18n.client.DateTimeFormat;
import java.util.Date;

public class DateFormatter {
  private static final long ONE_YEAR = 182L * 24 * 60 * 60 * 1000;

  private final DateTimeFormat sTime;
  private final DateTimeFormat sDate;
  private final DateTimeFormat sdtFmt;
  private final DateTimeFormat mDate;
  private final DateTimeFormat dtfmt;

  public DateFormatter(GeneralPreferences prefs) {
    String fmt_sTime = prefs.timeFormat().getFormat();
    String fmt_sDate = prefs.dateFormat().getShortFormat();
    String fmt_mDate = prefs.dateFormat().getLongFormat();

    sTime = DateTimeFormat.getFormat(fmt_sTime);
    sDate = DateTimeFormat.getFormat(fmt_sDate);
    sdtFmt = DateTimeFormat.getFormat(fmt_sDate + " " + fmt_sTime);
    mDate = DateTimeFormat.getFormat(fmt_mDate);
    dtfmt = DateTimeFormat.getFormat(fmt_mDate + " " + fmt_sTime);
  }

  /** Format a date using a really short format. */
  public String shortFormat(Date dt) {
    if (dt == null) {
      return "";
    }

    Date now = new Date();
    dt = new Date(dt.getTime());
    if (mDate.format(now).equals(mDate.format(dt))) {
      // Same day as today, report only the time.
      //
      return sTime.format(dt);

    } else if (Math.abs(now.getTime() - dt.getTime()) < ONE_YEAR) {
      // Within the last year, show a shorter date.
      //
      return sDate.format(dt);

    } else {
      // Report only date and year, its far away from now.
      //
      return mDate.format(dt);
    }
  }

  /** Format a date using a really short format. */
  public String shortFormatDayTime(Date dt) {
    if (dt == null) {
      return "";
    }

    Date now = new Date();
    dt = new Date(dt.getTime());
    if (mDate.format(now).equals(mDate.format(dt))) {
      // Same day as today, report only the time.
      //
      return sTime.format(dt);

    } else if (Math.abs(now.getTime() - dt.getTime()) < ONE_YEAR) {
      // Within the last year, show a shorter date.
      //
      return sdtFmt.format(dt);

    } else {
      // Report only date and year, its far away from now.
      //
      return mDate.format(dt);
    }
  }

  /** Format a date using the locale's medium length format. */
  public String mediumFormat(Date dt) {
    if (dt == null) {
      return "";
    }
    return dtfmt.format(new Date(dt.getTime()));
  }
}
