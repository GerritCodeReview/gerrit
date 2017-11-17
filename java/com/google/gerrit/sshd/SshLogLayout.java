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

import static com.google.gerrit.sshd.SshLog.P_ACCOUNT_ID;
import static com.google.gerrit.sshd.SshLog.P_AGENT;
import static com.google.gerrit.sshd.SshLog.P_EXEC;
import static com.google.gerrit.sshd.SshLog.P_MEMORY;
import static com.google.gerrit.sshd.SshLog.P_MESSAGE;
import static com.google.gerrit.sshd.SshLog.P_SESSION;
import static com.google.gerrit.sshd.SshLog.P_STATUS;
import static com.google.gerrit.sshd.SshLog.P_TOTAL_CPU;
import static com.google.gerrit.sshd.SshLog.P_USER_CPU;
import static com.google.gerrit.sshd.SshLog.P_USER_NAME;
import static com.google.gerrit.sshd.SshLog.P_WAIT;

import com.google.gerrit.util.logging.LogTimestampFormatter;
import java.nio.charset.StandardCharsets;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Node;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderFactory;
import org.apache.logging.log4j.core.layout.AbstractStringLayout;
import org.eclipse.jgit.util.QuotedString;

<<<<<<< PATCH SET (e78401 Migrate to log4j2)
@Plugin(
    name = "SshLogLayout",
    category = Node.CATEGORY,
    elementType = Layout.ELEMENT_TYPE,
    printObject = true)
public final class SshLogLayout extends AbstractStringLayout {
  protected final LogTimestampFormatter timestampFormatter;
=======
public final class SshLogLayout extends Layout {
  private final LogTimestampFormatter timestampFormatter;
>>>>>>> BASE      (f8fd64 Merge branch 'stable-3.8')

  public static class Builder<B extends Builder<B>> extends AbstractStringLayout.Builder<B>
      implements org.apache.logging.log4j.core.util.Builder<SshLogLayout> {

    public Builder() {
      super();
      setCharset(StandardCharsets.UTF_8);
    }

    @Override
    public SshLogLayout build() {
      return new SshLogLayout(getConfiguration());
    }
  }

  /** @deprecated Use {@link #newBuilder()} instead */
  @Deprecated
  public SshLogLayout() {
    this(null);
  }

  private SshLogLayout(final Configuration config) {
    super(config, StandardCharsets.UTF_8, null, null);

    timestampFormatter = new LogTimestampFormatter();
  }

  /** @deprecated Use {@link #newBuilder()} instead */
  @Deprecated
  public static SshLogLayout createLayout() {
    return new SshLogLayout(null);
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
<<<<<<< PATCH SET (e78401 Migrate to log4j2)
  public String toSerializable(LogEvent event) {
    final StringBuffer buf = new StringBuffer(128);
=======
  public String format(LoggingEvent event) {
    final StringBuilder buf = new StringBuilder(128);
>>>>>>> BASE      (f8fd64 Merge branch 'stable-3.8')

    buf.append('[');
    buf.append(timestampFormatter.format(event.getTimeMillis()));
    buf.append(']');

    req(P_SESSION, buf, event);

    buf.append(' ');
    buf.append('[');
    buf.append(event.getThreadName());
    buf.append(']');

    req(P_USER_NAME, buf, event);
    req(P_ACCOUNT_ID, buf, event);

    buf.append(' ');
    buf.append(event.getMessage().getFormattedMessage());

    String msg = (String) event.getMessage();
    if (!(msg.startsWith("LOGIN") || msg.equals("LOGOUT"))) {
      req(P_WAIT, buf, event);
      req(P_EXEC, buf, event);
      req(P_MESSAGE, buf, event);
      req(P_STATUS, buf, event);
      req(P_AGENT, buf, event);
      req(P_TOTAL_CPU, buf, event);
      req(P_USER_CPU, buf, event);
      req(P_MEMORY, buf, event);
    }

    buf.append('\n');
    return buf.toString();
  }

<<<<<<< PATCH SET (e78401 Migrate to log4j2)
  private void req(String key, StringBuffer buf, LogEvent event) {
    Object val = event.getContextData().getValue(key);
=======
  private void req(String key, StringBuilder buf, LoggingEvent event) {
    Object val = event.getMDC(key);
>>>>>>> BASE      (f8fd64 Merge branch 'stable-3.8')
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

<<<<<<< PATCH SET (e78401 Migrate to log4j2)
  private void opt(String key, StringBuffer buf, LogEvent event) {
    Object val = event.getContextData().getValue(key);
    if (val != null) {
      buf.append(' ');
      buf.append(val);
    }
  }
=======
  @Override
  public boolean ignoresThrowable() {
    return true;
  }

  @Override
  public void activateOptions() {}
>>>>>>> BASE      (f8fd64 Merge branch 'stable-3.8')
}
