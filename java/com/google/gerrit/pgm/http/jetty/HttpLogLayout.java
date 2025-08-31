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
import java.nio.charset.StandardCharsets;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.layout.AbstractStringLayout;
import org.apache.logging.log4j.util.ReadOnlyStringMap;

public final class HttpLogLayout extends AbstractStringLayout {
  private final LogTimestampFormatter timestampFormatter;

  public HttpLogLayout() {
    super(StandardCharsets.UTF_8);
    this.timestampFormatter = new LogTimestampFormatter();
  }

  @Override
  public String toSerializable(LogEvent event) {
    final StringBuilder buf = new StringBuilder(128);
    ReadOnlyStringMap context = event.getContextData();

    append(buf, context, HttpLog.P_HOST);

    buf.append(' ').append('[').append(event.getThreadName()).append(']');

    buf.append(" -"); // identd on client system (never requested)

    buf.append(' ');
    append(buf, context, HttpLog.P_USER);

    buf.append(' ')
        .append('[')
        .append(timestampFormatter.format(event.getTimeMillis()))
        .append(']');

    buf.append(' ');
    buf.append('"')
        .append(get(context, HttpLog.P_METHOD))
        .append(' ')
        .append(get(context, HttpLog.P_RESOURCE))
        .append(' ')
        .append(get(context, HttpLog.P_PROTOCOL))
        .append('"');

    buf.append(' ').append(get(context, HttpLog.P_STATUS));

    buf.append(' ').append(opt(context, HttpLog.P_CONTENT_LENGTH));
    buf.append(' ').append(opt(context, HttpLog.P_LATENCY));
    buf.append(' ').append(dqOpt(context, HttpLog.P_REFERER));
    buf.append(' ').append(dqOpt(context, HttpLog.P_USER_AGENT));
    buf.append(' ').append(opt(context, HttpLog.P_CPU_TOTAL));
    buf.append(' ').append(opt(context, HttpLog.P_CPU_USER));
    buf.append(' ').append(opt(context, HttpLog.P_MEMORY));
    buf.append(' ').append(dqOpt(context, HttpLog.P_COMMAND_STATUS));
    buf.append(' ').append(opt(context, HttpLog.P_TRACE_ID));

    buf.append('\n');
    return buf.toString();
  }

  private String get(ReadOnlyStringMap context, String key) {
    Object val = context.getValue(key);
    return val != null ? String.valueOf(val) : "-";
  }

  private void append(StringBuilder buf, ReadOnlyStringMap context, String key) {
    buf.append(get(context, key));
  }

  private String opt(ReadOnlyStringMap context, String key) {
    Object val = context.getValue(key);
    return val != null ? String.valueOf(val) : "-";
  }

  private String dqOpt(ReadOnlyStringMap context, String key) {
    Object val = context.getValue(key);
    if (val == null || String.valueOf(val).isEmpty()) {
      return "-";
    }
    return "\"" + val + "\"";
  }
}
