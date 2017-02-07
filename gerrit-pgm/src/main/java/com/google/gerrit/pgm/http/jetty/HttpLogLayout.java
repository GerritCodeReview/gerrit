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

package com.google.gerrit.pgm.http.jetty;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import org.apache.log4j.Layout;
import org.apache.log4j.spi.LoggingEvent;

public final class HttpLogLayout extends Layout {
  private final SimpleDateFormat dateFormat;
  private long lastTimeMillis;
  private String lastTimeString;

  public HttpLogLayout() {
    final TimeZone tz = TimeZone.getDefault();
    dateFormat = new SimpleDateFormat("dd/MMM/yyyy:HH:mm:ss Z");
    dateFormat.setTimeZone(tz);

    lastTimeMillis = System.currentTimeMillis();
    lastTimeString = dateFormat.format(new Date(lastTimeMillis));
  }

  @Override
  public String format(LoggingEvent event) {
    final StringBuilder buf = new StringBuilder(128);

    opt(buf, event, HttpLog.P_HOST);

    buf.append(' ');
    buf.append('-'); // identd on client system (never requested)

    buf.append(' ');
    opt(buf, event, HttpLog.P_USER);

    buf.append(' ');
    buf.append('[');
    formatDate(event.getTimeStamp(), buf);
    buf.append(']');

    buf.append(' ');
    buf.append('"');
    buf.append(event.getMDC(HttpLog.P_METHOD));
    buf.append(' ');
    buf.append(event.getMDC(HttpLog.P_RESOURCE));
    buf.append(' ');
    buf.append(event.getMDC(HttpLog.P_PROTOCOL));
    buf.append('"');

    buf.append(' ');
    buf.append(event.getMDC(HttpLog.P_STATUS));

    buf.append(' ');
    opt(buf, event, HttpLog.P_CONTENT_LENGTH);

    buf.append(' ');
    dq_opt(buf, event, HttpLog.P_REFERER);

    buf.append(' ');
    dq_opt(buf, event, HttpLog.P_USER_AGENT);

    buf.append('\n');
    return buf.toString();
  }

  private void opt(StringBuilder buf, LoggingEvent event, String key) {
    String val = (String) event.getMDC(key);
    if (val == null) {
      buf.append('-');
    } else {
      buf.append(val);
    }
  }

  private void dq_opt(StringBuilder buf, LoggingEvent event, String key) {
    String val = (String) event.getMDC(key);
    if (val == null) {
      buf.append('-');
    } else {
      buf.append('"');
      buf.append(val);
      buf.append('"');
    }
  }

  private void formatDate(final long now, final StringBuilder sbuf) {
    final long rounded = now - (int) (now % 1000);
    if (rounded != lastTimeMillis) {
      synchronized (dateFormat) {
        lastTimeMillis = rounded;
        lastTimeString = dateFormat.format(new Date(lastTimeMillis));
        sbuf.append(lastTimeString);
      }
    } else {
      sbuf.append(lastTimeString);
    }
  }

  @Override
  public boolean ignoresThrowable() {
    return true;
  }

  @Override
  public void activateOptions() {}
}
