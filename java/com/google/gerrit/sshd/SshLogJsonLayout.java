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

import static com.google.gerrit.sshd.SshLog.P_ACCOUNT_ID;
import static com.google.gerrit.sshd.SshLog.P_AGENT;
import static com.google.gerrit.sshd.SshLog.P_EXEC;
import static com.google.gerrit.sshd.SshLog.P_MEMORY;
import static com.google.gerrit.sshd.SshLog.P_MESSAGE;
import static com.google.gerrit.sshd.SshLog.P_SESSION;
import static com.google.gerrit.sshd.SshLog.P_STATUS;
import static com.google.gerrit.sshd.SshLog.P_TOTAL_CPU;
import static com.google.gerrit.sshd.SshLog.P_TRACE_ID;
import static com.google.gerrit.sshd.SshLog.P_USER_CPU;
import static com.google.gerrit.sshd.SshLog.P_USER_NAME;
import static com.google.gerrit.sshd.SshLog.P_WAIT;

import com.google.common.base.Splitter;
import com.google.gerrit.util.logging.JsonLayout;
import com.google.gerrit.util.logging.JsonLogEntry;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.logging.log4j.core.LogEvent;

public class SshLogJsonLayout extends JsonLayout {
  private static final Splitter SPLITTER = Splitter.on(" ");

  public SshLogJsonLayout() {
    super(StandardCharsets.UTF_8);
  }

  @Override
  public JsonLogEntry toJsonLogEntry(LogEvent event) {
    return new SshJsonLogEntry(event);
  }

  @SuppressWarnings("unused")
  private class SshJsonLogEntry extends JsonLogEntry {
    public String timestamp;
    public String session;
    public String traceId;
    public String thread;
    public String user;
    public String accountId;
    public String message;
    public String waitTime;
    public String execTime;
    public String totalCpu;
    public String userCpu;
    public String memory;
    public String status;
    public String agent;
    public String timeNegotiating;
    public String timeSearchReuse;
    public String timeSearchSizes;
    public String timeCounting;
    public String timeCompressing;
    public String timeWriting;
    public String timeTotal;
    public String bitmapIndexMisses;
    public String deltasTotal;
    public String objectsTotal;
    public String bytesTotal;

    public SshJsonLogEntry(LogEvent event) {
      this.timestamp = timestampFormatter.format(event.getTimeMillis());
      this.session = getMdcString(event, P_SESSION);
      this.traceId = getMdcString(event, P_TRACE_ID);
      this.thread = event.getThreadName();
      this.user = getMdcString(event, P_USER_NAME);
      this.accountId = getMdcString(event, P_ACCOUNT_ID);
      this.message = event.getMessage().getFormattedMessage();
      this.waitTime = getMdcString(event, P_WAIT);
      this.execTime = getMdcString(event, P_EXEC);
      this.totalCpu = getMdcString(event, P_TOTAL_CPU);
      this.userCpu = getMdcString(event, P_USER_CPU);
      this.memory = getMdcString(event, P_MEMORY);
      this.status = getMdcString(event, P_STATUS);
      this.agent = getMdcString(event, P_AGENT);

      String metricString = getMdcString(event, P_MESSAGE);
      if (metricString != null && !metricString.isEmpty()) {
        List<String> sshMetrics = SPLITTER.splitToList(metricString);
        this.timeNegotiating = sshMetrics.get(0);
        this.timeSearchReuse = sshMetrics.get(1);
        this.timeSearchSizes = sshMetrics.get(2);
        this.timeCounting = sshMetrics.get(3);
        this.timeCompressing = sshMetrics.get(4);
        this.timeWriting = sshMetrics.get(5);
        this.timeTotal = sshMetrics.get(6);
        this.bitmapIndexMisses = sshMetrics.get(7);
        this.deltasTotal = sshMetrics.get(8);
        this.objectsTotal = sshMetrics.get(9);
        this.bytesTotal = sshMetrics.get(10);
      }
    }
  }
}
