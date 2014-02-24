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

package com.google.gerrit.client;

import com.google.gwt.i18n.client.DateTimeFormat;
import com.google.gwtjsonrpc.common.JavaSqlTimestampHelper;

import java.util.Date;

/** Utility for replacing date/time strings in queries. */
class DateTimeReplacer {
  private static final String[] PREDICATES = new String[]{"before:", "after:"};
  private static DateTimeFormat tzFormat;

  static String replaceTimestamps(String query) {
    return replaceTimestamps(query, null);
  }

  // Visible for testing.
  static String replaceTimestamps(String query, String tz) {
    StringBuilder sb = new StringBuilder(query.length() + 30);
    boolean inQuote = false;
    boolean wordStart = true;
    CHARS: for (int i = 0; i < query.length(); i++) {
      if (wordStart) {
        for (String pred : PREDICATES) {
          int skip = replaceTimestamp(query, i, inQuote, sb, pred, tz);
          if (skip > 0) {
            i += skip;
            continue CHARS;
          }
        }
        wordStart = false;
      }
      char c = query.charAt(i);
      if (c == '"') {
        inQuote = !inQuote;
      } else if (c == ' ' && !inQuote) {
        wordStart = true;
      }
      sb.append(c);
    }
    return sb.toString();
  }

  private static int replaceTimestamp(String query, int start, boolean inQuote,
      StringBuilder out, String pred, String tz) {
    int l = pred.length();
    if (inQuote || !query.regionMatches(start, pred, 0, l)) {
      return 0;
    }
    int skip = l;

    StringBuilder term = new StringBuilder(
        query.length() + "\" HH:mm:ss.SSS -ZZZZ\"".length());
    inQuote = false;
    int i;
    LOOP: for (i = start + l; i < query.length(); i++) {
      char c = query.charAt(i);
      switch (c) {
        case '"':
          inQuote = !inQuote;
          break;
        case ' ':
          if (inQuote) {
            term.append(c);
          } else {
            skip--;
            break LOOP;
          }
          break;
        default:
          term.append(c);
          break;
      }
      skip++;
    }
    String ts = term.toString();
    if (!JavaSqlTimestampHelper.hasTime(ts)) {
      term.append(" 00:00:00");
      ts = term.toString();
    }
    if (!JavaSqlTimestampHelper.hasTimeZone(ts)) {
      term.append(' ').append(formatTimeZone(tz));
    }
    out.append(pred).append('"').append(term).append('"');
    return skip;
  }

  private static String formatTimeZone(String tz) {
    // Use passed-in timezone for tests, since DateTimeFormat is client-only.
    if (tz == null) {
      if (tzFormat == null) {
        tzFormat = DateTimeFormat.getFormat("Z");
      }
      return tzFormat.format(new Date());
    }
    return tz;
  }
}
