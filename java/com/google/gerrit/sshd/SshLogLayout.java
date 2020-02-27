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
import static com.google.gerrit.sshd.SshLog.P_MESSAGE;
import static com.google.gerrit.sshd.SshLog.P_SESSION;
import static com.google.gerrit.sshd.SshLog.P_STATUS;
import static com.google.gerrit.sshd.SshLog.P_USER_NAME;
import static com.google.gerrit.sshd.SshLog.P_WAIT;

import com.google.gerrit.util.logging.LogTimestampFormatter;
import org.apache.log4j.Layout;
import org.apache.log4j.spi.LoggingEvent;
import org.eclipse.jgit.util.QuotedString;

public final class SshLogLayout extends Layout {
  protected final LogTimestampFormatter timestampFormatter;

  public SshLogLayout() {
    timestampFormatter = new LogTimestampFormatter();
  }

  @Override
  public String format(LoggingEvent event) {
    final StringBuffer buf = new StringBuffer(128);

    buf.append('[');
    buf.append(timestampFormatter.format(event.getTimeStamp()));
    buf.append(']');

    req(P_SESSION, buf, event);

    buf.append(' ');
    buf.append('[');
    buf.append(event.getThreadName());
    buf.append(']');

    req(P_USER_NAME, buf, event);
    req(P_ACCOUNT_ID, buf, event);

    buf.append(' ');
    buf.append(event.getMessage());

    opt(P_WAIT, buf, event);
    opt(P_EXEC, buf, event);
    opt(P_MESSAGE, buf, event);
    opt(P_STATUS, buf, event);
    opt(P_AGENT, buf, event);

    buf.append('\n');
    return buf.toString();
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
