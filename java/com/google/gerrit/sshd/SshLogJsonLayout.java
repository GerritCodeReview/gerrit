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
import static com.google.gerrit.sshd.SshLog.P_USER_CPU;
import static com.google.gerrit.sshd.SshLog.P_USER_NAME;
import static com.google.gerrit.sshd.SshLog.P_WAIT;

import com.google.common.base.Splitter;
import com.google.gerrit.util.logging.JsonLayout;
import com.google.gerrit.util.logging.JsonLogEntry;
<<<<<<< PATCH SET (e78401 Migrate to log4j2)
import org.apache.logging.log4j.core.LogEvent;
=======
import java.util.List;
import org.apache.log4j.spi.LoggingEvent;
>>>>>>> BASE      (f8fd64 Merge branch 'stable-3.8')

public class SshLogJsonLayout extends JsonLayout {
  private static final Splitter SPLITTER = Splitter.on(" ");

  @Override
  public JsonLogEntry toJsonLogEntry(LogEvent event) {
    return new SshJsonLogEntry(event);
  }

  @SuppressWarnings("unused")
  private class SshJsonLogEntry extends JsonLogEntry {
    public String timestamp;
    /*public String session;
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
    public String bytesTotal;*/

    public SshJsonLogEntry(LogEvent event) {
      this.timestamp = timestampFormatter.format(event.getTimeMillis());
      /*this.session = getMdcString(event, P_SESSION);
      this.thread = event.getThreadName();
      this.user = getMdcString(event, P_USER_NAME);
      this.accountId = getMdcString(event, P_ACCOUNT_ID);
      this.message = (String) event.getMessage();
      this.waitTime = getMdcString(event, P_WAIT);
      this.execTime = getMdcString(event, P_EXEC);
      this.totalCpu = getMdcString(event, P_TOTAL_CPU);
      this.userCpu = getMdcString(event, P_USER_CPU);
      this.memory = getMdcString(event, P_MEMORY);
      this.status = getMdcString(event, P_STATUS);
      this.agent = getMdcString(event, P_AGENT);

      String metricString = getMdcString(event, P_MESSAGE);
      if (metricString != null && !metricString.isEmpty()) {
<<<<<<< PATCH SET (e78401 Migrate to log4j2)
        String[] ssh_metrics = metricString.split(" ");
        this.timeNegotiating = ssh_metrics[0];
        this.timeSearchReuse = ssh_metrics[1];
        this.timeSearchSizes = ssh_metrics[2];
        this.timeCounting = ssh_metrics[3];
        this.timeCompressing = ssh_metrics[4];
        this.timeWriting = ssh_metrics[5];
        this.timeTotal = ssh_metrics[6];
        this.bitmapIndexMisses = ssh_metrics[7];
        this.deltasTotal = ssh_metrics[8];
        this.objectsTotal = ssh_metrics[9];
        this.bytesTotal = ssh_metrics[10];
      }*/
=======
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
>>>>>>> BASE      (f8fd64 Merge branch 'stable-3.8')
    }
  }
}
