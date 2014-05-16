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

package com.google.gerrit.httpd.plugins;

import com.google.common.base.Strings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import javax.servlet.http.HttpServletRequest;

class HttpIfModifiedSinceDate {
  private static final Logger log = LoggerFactory
      .getLogger(HttpIfModifiedSinceDate.class);
  private static ThreadLocal<DateFormat[]> dateFormatsLocal =
      new ThreadLocal<DateFormat[]>();

  private static DateFormat[] dateFormats() {
    if (dateFormatsLocal.get() == null) {
      dateFormatsLocal.set(new DateFormat[] {
          dateFormatOf("EEEE, dd-MMM-yy HH:mm:ss zzz"),
          dateFormatOf("EEE, dd MMM yyyy HH:mm:ss zzz"),
          dateFormatOf("EEE MMM d HH:mm:ss yyyy")});
    }
    return dateFormatsLocal.get();
  }

  static Date parse(HttpServletRequest req) {
    String dateValue = req.getHeader("If-Modified-Since");
    if (!Strings.isNullOrEmpty(dateValue)) {
      dateValue = stripSingleQuotes(dateValue);
      for (DateFormat fmt : dateFormats()) {
        try {
          return fmt.parse(dateValue);
        } catch (ParseException pe) {
          // Try next parser
        }
      }
      log.warn("Cannot parse If-Modified-Since date '" + dateValue + "'");
      return null;
    } else {
      return null;
    }
  }

  private static DateFormat dateFormatOf(String fmt) {
    SimpleDateFormat dateFmt = new SimpleDateFormat(fmt);
    Calendar y2kCalendar = Calendar.getInstance();
    y2kCalendar.setTimeZone(TimeZone.getTimeZone("GMT"));
    y2kCalendar.set(2000, Calendar.JANUARY, 1, 0, 0, 0);
    y2kCalendar.set(Calendar.MILLISECOND, 0);
    dateFmt.set2DigitYearStart(y2kCalendar.getTime());
    return dateFmt;
  }

  private static String stripSingleQuotes(String dateValue) {
    if (dateValue.length() > 1 && dateValue.startsWith("'")
        && dateValue.endsWith("'")) {
      dateValue = dateValue.substring(1, dateValue.length() - 1);
    }
    return dateValue;
  }
}
