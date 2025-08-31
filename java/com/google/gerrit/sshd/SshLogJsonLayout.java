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
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.layout.AbstractStringLayout;

@Plugin(name = "SshLogJsonLayout", category = "Core", elementType = "layout", printObject = true)
public class SshLogJsonLayout extends AbstractStringLayout {
  private static final Splitter SPLITTER = Splitter.on(" ");

  protected SshLogJsonLayout() {
    super(StandardCharsets.UTF_8);
  }

  @PluginFactory
  public static SshLogJsonLayout createLayout() {
    return new SshLogJsonLayout();
  }

  @Override
  public String toSerializable(LogEvent event) {
    return toJson(new SshJsonLogEntry(event)) + "\n";
  }

  private static String toJson(SshJsonLogEntry entry) {
    StringBuilder sb = new StringBuilder();
    sb.append("{");
    appendField(sb, "timestamp", entry.timestamp);
    appendField(sb, "session", entry.session);
    appendField(sb, "traceId", entry.traceId);
    appendField(sb, "thread", entry.thread);
    appendField(sb, "user", entry.user);
    appendField(sb, "accountId", entry.accountId);
    appendField(sb, "message", entry.message);
    appendField(sb, "waitTime", entry.waitTime);
    appendField(sb, "execTime", entry.execTime);
    appendField(sb, "totalCpu", entry.totalCpu);
    appendField(sb, "userCpu", entry.userCpu);
    appendField(sb, "memory", entry.memory);
    appendField(sb, "status", entry.status);
    appendField(sb, "agent", entry.agent);
    appendField(sb, "timeNegotiating", entry.timeNegotiating);
    appendField(sb, "timeSearchReuse", entry.timeSearchReuse);
    appendField(sb, "timeSearchSizes", entry.timeSearchSizes);
    appendField(sb, "timeCounting", entry.timeCounting);
    appendField(sb, "timeCompressing", entry.timeCompressing);
    appendField(sb, "timeWriting", entry.timeWriting);
    appendField(sb, "timeTotal", entry.timeTotal);
    appendField(sb, "bitmapIndexMisses", entry.bitmapIndexMisses);
    appendField(sb, "deltasTotal", entry.deltasTotal);
    appendField(sb, "objectsTotal", entry.objectsTotal);
    appendField(sb, "bytesTotal", entry.bytesTotal, true);
    sb.append("}");
    return sb.toString();
  }

  private static void appendField(StringBuilder sb, String key, String value) {
    appendField(sb, key, value, false);
  }

  private static void appendField(StringBuilder sb, String key, String value, boolean last) {
    if (value != null) {
      sb.append("\"").append(key).append("\":\"").append(value.replace("\"", "\\\"")).append("\"");
      if (!last) sb.append(",");
    }
  }

  @SuppressWarnings("unused")
  private static class SshJsonLogEntry {
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
      this.timestamp =
          java.time.format.DateTimeFormatter.ISO_INSTANT.format(
              java.time.Instant.ofEpochMilli(event.getTimeMillis()));
      this.session = ThreadContext.get(P_SESSION);
      this.traceId = ThreadContext.get(P_TRACE_ID);
      this.thread = event.getThreadName();
      this.user = ThreadContext.get(P_USER_NAME);
      this.accountId = ThreadContext.get(P_ACCOUNT_ID);
      this.message = event.getMessage().getFormattedMessage();
      this.waitTime = ThreadContext.get(P_WAIT);
      this.execTime = ThreadContext.get(P_EXEC);
      this.totalCpu = ThreadContext.get(P_TOTAL_CPU);
      this.userCpu = ThreadContext.get(P_USER_CPU);
      this.memory = ThreadContext.get(P_MEMORY);
      this.status = ThreadContext.get(P_STATUS);
      this.agent = ThreadContext.get(P_AGENT);

      String metricString = ThreadContext.get(P_MESSAGE);
      if (metricString != null && !metricString.isEmpty()) {
        List<String> ssh_metrics = SPLITTER.splitToList(metricString);
        this.timeNegotiating = ssh_metrics.get(0);
        this.timeSearchReuse = ssh_metrics.get(1);
        this.timeSearchSizes = ssh_metrics.get(2);
        this.timeCounting = ssh_metrics.get(3);
        this.timeCompressing = ssh_metrics.get(4);
        this.timeWriting = ssh_metrics.get(5);
        this.timeTotal = ssh_metrics.get(6);
        this.bitmapIndexMisses = ssh_metrics.get(7);
        this.deltasTotal = ssh_metrics.get(8);
        this.objectsTotal = ssh_metrics.get(9);
        this.bytesTotal = ssh_metrics.get(10);
      }
    }
  }
}
