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

package com.google.gerrit.pgm.http.jetty;

import static com.google.gerrit.pgm.http.jetty.HttpLog.P_CONTENT_LENGTH;
import static com.google.gerrit.pgm.http.jetty.HttpLog.P_HOST;
import static com.google.gerrit.pgm.http.jetty.HttpLog.P_LATENCY;
import static com.google.gerrit.pgm.http.jetty.HttpLog.P_METHOD;
import static com.google.gerrit.pgm.http.jetty.HttpLog.P_PROTOCOL;
import static com.google.gerrit.pgm.http.jetty.HttpLog.P_REFERER;
import static com.google.gerrit.pgm.http.jetty.HttpLog.P_RESOURCE;
import static com.google.gerrit.pgm.http.jetty.HttpLog.P_STATUS;
import static com.google.gerrit.pgm.http.jetty.HttpLog.P_USER;
import static com.google.gerrit.pgm.http.jetty.HttpLog.P_USER_AGENT;

import com.google.gerrit.util.logging.JsonLayout;
import com.google.gerrit.util.logging.JsonLogEntry;
import org.apache.log4j.spi.LoggingEvent;

public class HttpLogJsonLayout extends JsonLayout {

  @Override
  public JsonLogEntry toJsonLogEntry(LoggingEvent event) {
    return new HttpJsonLogEntry(event);
  }

  @SuppressWarnings("unused")
  private class HttpJsonLogEntry extends JsonLogEntry {
    public String host;
    public String thread;
    public String user;
    public String timestamp;
    public String method;
    public String resource;
    public String protocol;
    public String status;
    public String contentLength;
    public String latency;
    public String referer;
    public String userAgent;

    public HttpJsonLogEntry(LoggingEvent event) {
      this.host = getMdcString(event, P_HOST);
      this.thread = event.getThreadName();
      this.user = getMdcString(event, P_USER);
      this.timestamp = timestampFormatter.format(event.getTimeStamp());
      this.method = getMdcString(event, P_METHOD);
      this.resource = getMdcString(event, P_RESOURCE);
      this.protocol = getMdcString(event, P_PROTOCOL);
      this.status = getMdcString(event, P_STATUS);
      this.contentLength = getMdcString(event, P_CONTENT_LENGTH);
      this.latency = getMdcString(event, P_LATENCY);
      this.referer = getMdcString(event, P_REFERER);
      this.userAgent = getMdcString(event, P_USER_AGENT);
    }
  }
}
