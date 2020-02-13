// Copyright (C) 2020 The Android Open Source Project
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
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.TimeZone;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.log4j.Layout;
import org.apache.log4j.spi.LoggingEvent;

public class SshLogJsonLayout extends Layout {

  private long lastTimeMillis;
  private String lastTimeString;
  private final SimpleDateFormat dateFormat;

  public SshLogJsonLayout() {
    final TimeZone tz = TimeZone.getDefault();

    dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss,SSS Z");
    dateFormat.setTimeZone(tz);
  }

  @Override
  public String format(LoggingEvent event) {
    final StringBuilder buf = new StringBuilder(128);
    buf.append('{');

    HashMap<String, String> kvMap = new HashMap<>();
    kvMap.put("timestamp", formatDate(event.getTimeStamp()));
    kvMap.put("session", (String) event.getMDC(SshLog.P_SESSION));
    kvMap.put("thread", event.getThreadName());
    kvMap.put("user", (String) event.getMDC(SshLog.P_USER_NAME));
    kvMap.put("account_id", (String) event.getMDC(SshLog.P_ACCOUNT_ID));
    kvMap.put("message", (String) event.getMessage());
    kvMap.put("wait_time", (String) event.getMDC(SshLog.P_WAIT));
    kvMap.put("exec_time", (String) event.getMDC(SshLog.P_EXEC));
    kvMap.put("mdc_message", (String) event.getMDC(SshLog.P_MESSAGE));
    kvMap.put("status", (String) event.getMDC(SshLog.P_STATUS));
    kvMap.put("agent", (String) event.getMDC(SshLog.P_AGENT));

    List<String> formattedKV =
        kvMap.entrySet().stream()
            .filter(kv -> kv.getValue() != null)
            .flatMap(kv -> Stream.of(String.format("\"%s\":\"%s\"", kv.getKey(), kv.getValue())))
            .collect(Collectors.toList());

    buf.append(String.join(",", formattedKV));
    buf.append('}');
    buf.append('\n');
    return buf.toString();
  }

  private String formatDate(long now) {
    final long rounded = now - (int) (now % 1000);
    if (rounded != lastTimeMillis) {
      synchronized (dateFormat) {
        lastTimeMillis = rounded;
        lastTimeString = dateFormat.format(new Date(lastTimeMillis));
        return lastTimeString;
      }
    }

    return lastTimeString;
  }

  @Override
  public boolean ignoresThrowable() {
    return true;
  }

  @Override
  public void activateOptions() {}
}
