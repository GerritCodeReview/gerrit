// Copyright (C) 2013 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// You may not use this file except in compliance with the License.
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
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.layout.AbstractStringLayout;
import org.apache.logging.log4j.util.Strings;

import java.nio.charset.Charset;

public final class HttpLogLayout extends AbstractStringLayout {
  private final LogTimestampFormatter timestampFormatter;

  public HttpLogLayout() {
    super(Charset.defaultCharset());
    this.timestampFormatter = new LogTimestampFormatter();
  }

  @Override
  public String toSerializable(LogEvent event) {
    final StringBuilder buf = new StringBuilder(128);

    opt(buf, event, HttpLog.P_HOST);

    buf.append(' ').append('[').append(event.getThreadName()).append(']');

    buf.append(' ').append('-'); // identd on client system (never requested)

    buf.append(' ');
    opt(buf, event, HttpLog.P_USER);

    buf.append(' ').append('[').append(timestampFormatter.format(event.getTimeMillis())).append(']');

    buf.append(' ');
    buf.append('"')
       .append(String.valueOf(event.getContextData().getValue(HttpLog.P_METHOD)))
       .append(' ')
       .append(String.valueOf(event.getContextData().getValue(HttpLog.P_RESOURCE)))
       .append(' ')
       .append(String.valueOf(event.getContextData().getValue(HttpLog.P_PROTOCOL)))
       .append('"');

    buf.append(' ').append(String.valueOf(event.getContextData().getValue(HttpLog.P_STATUS)));

    buf.append(' ');
    opt(buf, event, HttpLog.P_CONTENT_LENGTH);

    buf.append(' ');
    opt(buf, event, HttpLog.P_LATENCY);

    buf.append(' ');
    dq_opt(buf, event, HttpLog.P_REFERER);

    buf.append(' ');
    dq_opt(buf, event, HttpLog.P_USER_AGENT);

    buf.append(' ');
    opt(buf, event, HttpLog.P_CPU_TOTAL);

    buf.append(' ');
    opt(buf, event, HttpLog.P_CPU_USER);

    buf.append(' ');
    opt(buf, event, HttpLog.P_MEMORY);

    buf.append(' ');
    dq_opt(buf, event, HttpLog.P_COMMAND_STATUS);

    buf.append(' ');
    opt(buf, event, HttpLog.P_TRACE_ID);

    buf.append('\n');
    return buf.toString();
  }

  private void opt(StringBuilder buf, LogEvent event, String key) {
    String val = event.getContextData().getValue(key);
    if (Strings.isEmpty(val)) {
      buf.append('-');
    } else {
      buf.append(val);
    }
  }

  private void dq_opt(StringBuilder buf, LogEvent event, String key) {
    String val = event.getContextData().getValue(key);
    if (Strings.isEmpty(val)) {
      buf.append('-');
    } else {
      buf.append('"').append(val).append('"');
    }
  }
}
