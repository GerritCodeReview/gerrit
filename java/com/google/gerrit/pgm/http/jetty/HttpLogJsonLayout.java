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
import static com.google.gerrit.pgm.http.jetty.HttpLog.P_METHOD;
import static com.google.gerrit.pgm.http.jetty.HttpLog.P_PROTOCOL;
import static com.google.gerrit.pgm.http.jetty.HttpLog.P_REFERER;
import static com.google.gerrit.pgm.http.jetty.HttpLog.P_RESOURCE;
import static com.google.gerrit.pgm.http.jetty.HttpLog.P_STATUS;
import static com.google.gerrit.pgm.http.jetty.HttpLog.P_USER;
import static com.google.gerrit.pgm.http.jetty.HttpLog.P_USER_AGENT;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;
import org.apache.log4j.Layout;
import org.apache.log4j.spi.LoggingEvent;

public class HttpLogJsonLayout extends Layout {
  private final DateTimeFormatter dateFormatter;
  private final Gson gson;
  private final ZoneOffset timeOffset;

  public HttpLogJsonLayout() {
    dateFormatter = DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss,SSS Z");
    timeOffset = OffsetDateTime.now().getOffset();

    gson = newGson();
  }

  @Override
  public String format(LoggingEvent event) {
    HttpJsonLog logEntry = new HttpJsonLog(event);
    return gson.toJson(logEntry) + "\n";
  }

  @Override
  public boolean ignoresThrowable() {
    return true;
  }

  @Override
  public void activateOptions() {}

  private static Gson newGson() {
    GsonBuilder gb =
        new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .disableHtmlEscaping();
    return gb.create();
  }

  @SuppressWarnings("unused")
  private class HttpJsonLog {
    public String host;
    public String thread;
    public String user;
    public String timestamp;
    public String method;
    public String resource;
    public String protocol;
    public String status;
    public String contentLength;
    public String referer;
    public String userAgent;

    public HttpJsonLog(LoggingEvent event) {
      this.host = getMdcString(event, P_HOST);
      this.thread = event.getThreadName();
      this.user = getMdcString(event, P_USER);
      this.timestamp = formatDate(event.getTimeStamp());
      this.method = getMdcString(event, P_METHOD);
      this.resource = getMdcString(event, P_RESOURCE);
      this.protocol = getMdcString(event, P_PROTOCOL);
      this.status = getMdcString(event, P_STATUS);
      this.contentLength = getMdcString(event, P_CONTENT_LENGTH);
      this.referer = getMdcString(event, P_REFERER);
      this.userAgent = getMdcString(event, P_USER_AGENT);
    }

    private String getMdcString(LoggingEvent event, String key) {
      return (String) event.getMDC(key);
    }

    private String formatDate(long now) {
      int nanoSeconds = (int) TimeUnit.NANOSECONDS.convert(now % 1000, TimeUnit.MILLISECONDS);
      return ZonedDateTime.of(
              LocalDateTime.ofEpochSecond(now / 1000, nanoSeconds, timeOffset),
              ZoneId.systemDefault())
          .format(dateFormatter);
    }
  }
}
