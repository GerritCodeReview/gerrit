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

import com.google.gerrit.util.logging.LogTimestampFormatter;
import org.apache.log4j.Layout;
import org.apache.log4j.spi.LoggingEvent;

public final class HttpLogLayout extends Layout {
  private final LogTimestampFormatter timestampFormatter;

  public HttpLogLayout() {
    timestampFormatter = new LogTimestampFormatter();
  }

  @Override
  public String format(LoggingEvent event) {
    final StringBuilder buf = new StringBuilder(128);

    opt(buf, event, HttpLog.P_HOST);

    buf.append(' ');
    buf.append('[');
    buf.append(event.getThreadName());
    buf.append(']');

    buf.append(' ');
    buf.append('-'); // identd on client system (never requested)

    buf.append(' ');
    opt(buf, event, HttpLog.P_USER);

    buf.append(' ');
    buf.append('[');
    buf.append(timestampFormatter.format(event.getTimeStamp()));
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
    opt(buf, event, HttpLog.P_LATENCY);

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

  @Override
  public boolean ignoresThrowable() {
    return true;
  }

  @Override
  public void activateOptions() {}
}
