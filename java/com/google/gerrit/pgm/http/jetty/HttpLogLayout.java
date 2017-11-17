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
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Node;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderFactory;
import org.apache.logging.log4j.core.layout.AbstractStringLayout;

@Plugin(
    name = "HttpLogLayout",
    category = Node.CATEGORY,
    elementType = Layout.ELEMENT_TYPE,
    printObject = true)
public final class HttpLogLayout extends AbstractStringLayout {
  private final LogTimestampFormatter timestampFormatter;


  public static class Builder<B extends Builder<B>> extends AbstractStringLayout.Builder<B>
      implements org.apache.logging.log4j.core.util.Builder<HttpLogLayout> {

    public Builder() {
      super();
      setCharset(StandardCharsets.UTF_8);
    }

    @Override
    public HttpLogLayout build() {
      return new HttpLogLayout(getConfiguration());
    }
  }

  /** @deprecated Use {@link #newBuilder()} instead */
  @Deprecated
  public HttpLogLayout() {
    this(null);
  }

  private HttpLogLayout(final Configuration config) {
    super(config, StandardCharsets.UTF_8, null, null);

    timestampFormatter = new LogTimestampFormatter();
  }

  /** @deprecated Use {@link #newBuilder()} instead */
  @Deprecated
  public static HttpLogLayout createLayout() {
    return new HttpLogLayout(null);
  }

  @PluginBuilderFactory
  public static <B extends Builder<B>> B newBuilder() {
    return new Builder<B>().asBuilder();
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
    buf.append('[');
    buf.append(event.getThreadName());
    buf.append(']');

    buf.append(' ');
    buf.append('-'); // identd on client system (never requested)

    buf.append(' ');
    opt(buf, HttpLog.P_USER, event);

    buf.append(' ');
    buf.append('[');
    buf.append(timestampFormatter.format(event.getTimeMillis()));
    buf.append(']');

    buf.append(' ');
    buf.append('"');
    String val = event.getContextData().getValue(HttpLog.P_METHOD);
    buf.append(val);
    buf.append(' ');
    String val2 = event.getContextData().getValue(HttpLog.P_RESOURCE);
    buf.append(val2);
    buf.append(' ');
    String val3 = event.getContextData().getValue(HttpLog.P_PROTOCOL);
    buf.append(val3);
    buf.append('"');

    buf.append(' ');
    String val4 = event.getContextData().getValue(HttpLog.P_STATUS);
    buf.append(val4);

    buf.append(' ');
    opt(buf, HttpLog.P_CONTENT_LENGTH, event);

    buf.append(' ');
    opt(buf, HttpLog.P_LATENCY, event);

    buf.append(' ');
    dq_opt(buf, HttpLog.P_REFERER, event);

    buf.append(' ');
    dq_opt(buf, HttpLog.P_USER_AGENT, event);

    buf.append(' ');
    opt(buf, event, HttpLog.P_CPU_TOTAL);

    buf.append(' ');
    opt(buf, event, HttpLog.P_CPU_USER);

    buf.append(' ');
    opt(buf, event, HttpLog.P_MEMORY);

    buf.append(' ');
    dq_opt(buf, event, HttpLog.P_COMMAND_STATUS);

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
}
