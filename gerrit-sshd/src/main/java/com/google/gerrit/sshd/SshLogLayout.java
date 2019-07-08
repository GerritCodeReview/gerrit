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

package com.google.gerrit.sshd;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;
import org.apache.log4j.Layout;
import org.apache.log4j.spi.LoggingEvent;
import org.eclipse.jgit.util.QuotedString;

public final class SshLogLayout extends Layout {

  private static final String P_SESSION = "session";
  private static final String P_USER_NAME = "userName";
  private static final String P_ACCOUNT_ID = "accountId";
  private static final String P_WAIT = "queueWaitTime";
  private static final String P_EXEC = "executionTime";
  private static final String P_STATUS = "status";
  private static final String P_AGENT = "agent";

  private final Calendar calendar;
  private long lastTimeMillis;
  private final char[] lastTimeString = new char[20];
  private final SimpleDateFormat tzFormat;
  private char[] timeZone;

  public SshLogLayout() {
    final TimeZone tz = TimeZone.getDefault();
    calendar = Calendar.getInstance(tz);

    tzFormat = new SimpleDateFormat("Z");
    tzFormat.setTimeZone(tz);
  }

  @Override
  public String format(LoggingEvent event) {
    final StringBuffer buf = new StringBuffer(128);

    buf.append('[');
    formatDate(event.getTimeStamp(), buf);
    buf.append(']');

    req(P_SESSION, buf, event);
    req(P_USER_NAME, buf, event);
    req(P_ACCOUNT_ID, buf, event);

    buf.append(' ');
    buf.append(event.getMessage());

    opt(P_WAIT, buf, event);
    opt(P_EXEC, buf, event);
    opt(P_STATUS, buf, event);
    opt(P_AGENT, buf, event);

    buf.append('\n');
    return buf.toString();
  }

  private void formatDate(final long now, final StringBuffer sbuf) {
    final int millis = (int) (now % 1000);
    final long rounded = now - millis;
    if (rounded != lastTimeMillis) {
      synchronized (calendar) {
        final int start = sbuf.length();
        calendar.setTimeInMillis(rounded);
        sbuf.append(calendar.get(Calendar.YEAR));
        sbuf.append('-');
        sbuf.append(toTwoDigits(calendar.get(Calendar.MONTH) + 1));
        sbuf.append('-');
        sbuf.append(toTwoDigits(calendar.get(Calendar.DAY_OF_MONTH)));
        sbuf.append(' ');
        sbuf.append(toTwoDigits(calendar.get(Calendar.HOUR_OF_DAY)));
        sbuf.append(':');
        sbuf.append(toTwoDigits(calendar.get(Calendar.MINUTE)));
        sbuf.append(':');
        sbuf.append(toTwoDigits(calendar.get(Calendar.SECOND)));
        sbuf.append(',');
        sbuf.getChars(start, sbuf.length(), lastTimeString, 0);
        lastTimeMillis = rounded;
        timeZone = tzFormat.format(calendar.getTime()).toCharArray();
      }
    } else {
      sbuf.append(lastTimeString);
    }
    sbuf.append(String.format("%03d", millis));
    sbuf.append(' ');
    sbuf.append(timeZone);
  }

  private String toTwoDigits(int input) {
    return String.format("%02d", input);
  }

  private void req(String key, StringBuffer buf, LoggingEvent event) {
    Object val = event.getMDC(key);
    buf.append(' ');
    if (val != null) {
      String s = val.toString();
      if (0 <= s.indexOf(' ')) {
        buf.append(QuotedString.BOURNE.quote(s));
      } else {
        buf.append(val);
      }
    } else {
      buf.append('-');
    }
  }

  private void opt(String key, StringBuffer buf, LoggingEvent event) {
    Object val = event.getMDC(key);
    if (val != null) {
      buf.append(' ');
      buf.append(val);
    }
  }

  @Override
  public boolean ignoresThrowable() {
    return true;
  }

  @Override
  public void activateOptions() {}
}
