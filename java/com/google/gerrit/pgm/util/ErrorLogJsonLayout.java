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

package com.google.gerrit.pgm.util;

import com.google.gerrit.util.logging.JsonLayout;
import com.google.gerrit.util.logging.JsonLogEntry;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.log4j.spi.ThrowableInformation;

public class ErrorLogJsonLayout extends JsonLayout {

  @Override
  public JsonLogEntry toJsonLogEntry(LoggingEvent event) {
    return new ErrorJsonLogEntry(event);
  }

  @SuppressWarnings("unused")
  private class ErrorJsonLogEntry extends JsonLogEntry {
    public String timestamp;
    public String sourceHost;
    public String message;
    public Map<String, String> exception;
    public String file;
    public String lineNumber;
    public String className;
    public String method;
    public String loggerName;

    @SuppressWarnings("rawtypes")
    public Map mdc;

    public String ndc;
    public String level;
    public String threadName;

    public ErrorJsonLogEntry(LoggingEvent event) {
      this.timestamp = formatDate(event.getTimeStamp());
      this.sourceHost = getSourceHost();
      this.message = event.getRenderedMessage();
      if (event.getThrowableInformation() != null) {
        this.exception = getException(event.getThrowableInformation());
      }
      this.file = event.getLocationInformation().getFileName();
      this.lineNumber = event.getLocationInformation().getLineNumber();
      this.className = event.getLocationInformation().getClassName();
      this.method = event.getLocationInformation().getMethodName();
      this.loggerName = event.getLoggerName();
      this.mdc = event.getProperties();
      this.ndc = event.getNDC();
      this.level = event.getLevel().toString();
      this.threadName = event.getThreadName();
    }

    private String getSourceHost() {
      try {
        return InetAddress.getLocalHost().getHostName();
      } catch (UnknownHostException e) {
        return "unknown-host";
      }
    }

    private Map<String, String> getException(ThrowableInformation throwable) {
      HashMap<String, String> exceptionInformation = new HashMap<>();
      if (throwable.getThrowable().getClass().getCanonicalName() != null) {
        exceptionInformation.put(
            "exception_class", throwable.getThrowable().getClass().getCanonicalName());
      }
      if (throwable.getThrowable().getMessage() != null) {
        exceptionInformation.put("exception_message", throwable.getThrowable().getMessage());
      }
      if (throwable.getThrowableStrRep() != null) {
        String stackTrace = String.join("\n", throwable.getThrowableStrRep());
        exceptionInformation.put("stacktrace", stackTrace);
      }
      return exceptionInformation;
    }
  }
}
