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

import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.layout.AbstractStringLayout;

@Plugin(name = "HttpLogLayout", category = "Core", elementType = "layout", printObject = true)
public final class HttpLogLayout extends AbstractStringLayout {
  private final SimpleDateFormat dateFormat;
  private long lastTimeMillis;
  private String lastTimeString;

  public HttpLogLayout(Charset charset) {
    super(charset);

    final TimeZone tz = TimeZone.getDefault();
    dateFormat = new SimpleDateFormat("dd/MMM/yyyy:HH:mm:ss Z");
    dateFormat.setTimeZone(tz);

    lastTimeMillis = System.currentTimeMillis();
    lastTimeString = dateFormat.format(new Date(lastTimeMillis));
  }

  /**
   * Formats a {@link org.apache.logging.log4j.core.LogEvent} in conformance with the BSD Log record
   * format.
   *
   * @param event The LogEvent
   * @return the event formatted as a String.
   */
  @Override
  public String toSerializable(final LogEvent event) {
    final StringBuilder buf = new StringBuilder(128);

    opt(buf, HttpLog.P_HOST, event);

    buf.append(' ');
    buf.append('-'); // identd on client system (never requested)

    buf.append(' ');
    opt(buf, HttpLog.P_USER, event);

    buf.append(' ');
    buf.append('[');
    formatDate(event.getTimeMillis(), buf);
    buf.append(']');

    buf.append(' ');
    buf.append('"');
    buf.append(event.getContextData().getValue(HttpLog.P_METHOD));
    buf.append(' ');
    buf.append(event.getContextData().getValue(HttpLog.P_RESOURCE));
    buf.append(' ');
    buf.append(event.getContextData().getValue(HttpLog.P_PROTOCOL));
    buf.append('"');

    buf.append(' ');
    buf.append(event.getContextData().getValue(HttpLog.P_STATUS));

    buf.append(' ');
    opt(buf, HttpLog.P_CONTENT_LENGTH, event);

    buf.append(' ');
    dq_opt(buf, HttpLog.P_REFERER, event);

    buf.append(' ');
    dq_opt(buf, HttpLog.P_USER_AGENT, event);

    buf.append('\n');
    return buf.toString();
  }

  private void opt(StringBuilder buf, String key, LogEvent event) {
    String val = event.getContextData().getValue(key);
    if (val == null) {
      buf.append('-');
    } else {
      buf.append(val);
    }
  }

  private void dq_opt(StringBuilder buf, String key, LogEvent event) {
    String val = event.getContextData().getValue(key);
    if (val == null) {
      buf.append('-');
    } else {
      buf.append('"');
      buf.append(val);
      buf.append('"');
    }
  }

  private void formatDate(long now, StringBuilder sbuf) {
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
}
