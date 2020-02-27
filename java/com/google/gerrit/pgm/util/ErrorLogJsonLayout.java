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
import com.google.gson.annotations.SerializedName;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.log4j.spi.ThrowableInformation;

/** Layout for formatting error log events in the JSON format. */
public class ErrorLogJsonLayout extends JsonLayout {

  @Override
  public DateTimeFormatter createDateTimeFormatter() {
    return DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
  }

  @Override
  public JsonLogEntry toJsonLogEntry(LoggingEvent event) {
    return new ErrorJsonLogEntry(event);
  }

  @SuppressWarnings("unused")
  private class ErrorJsonLogEntry extends JsonLogEntry {
    /** Timestamp of when the log entry was created. */
    @SerializedName("@timestamp")
    public final String timestamp;

    /** Hostname of the machine running Gerrit. */
    public final String sourceHost;
    /** Logged message. */
    public final String message;
    /** File containing the code creating the log entry. */
    public final String file;
    /** Line number of code creating the log entry. */
    public final String lineNumber;

    /** Class from which the log entry was created. */
    @SerializedName("class")
    public final String clazz;

    /** Method from which the log entry was created. */
    public final String method;
    /** Name of the logger creating the log entry. */
    public final String loggerName;

    /** Mapped diagnostic context. */
    @SuppressWarnings("rawtypes")
    public final Map mdc;

    /** Nested diagnostic context. */
    public final String ndc;
    /** Logging level/severity. */
    public final String level;
    /** Thread executing the code creating the log entry. */
    public final String threadName;

    /** Version of log format. */
    @SerializedName("@version")
    public final int version = 2;

    /**
     * Map containing information of a logged exception. It contains the following key-value pairs:
     * exception_class: Which class threw the exception exception_method: Which method threw the
     * exception stacktrace: The exception stacktrace
     */
    public Map<String, String> exception;

    public ErrorJsonLogEntry(LoggingEvent event) {
      this.timestamp = formatDate(event.getTimeStamp());
      this.sourceHost = getSourceHost();
      this.message = event.getRenderedMessage();
      this.file = event.getLocationInformation().getFileName();
      this.lineNumber = event.getLocationInformation().getLineNumber();
      this.clazz = event.getLocationInformation().getClassName();
      this.method = event.getLocationInformation().getMethodName();
      this.loggerName = event.getLoggerName();
      this.mdc = event.getProperties();
      this.ndc = event.getNDC();
      this.level = event.getLevel().toString();
      this.threadName = event.getThreadName();
      if (event.getThrowableInformation() != null) {
        this.exception = getException(event.getThrowableInformation());
      }
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

      String throwableName = throwable.getThrowable().getClass().getCanonicalName();
      if (throwableName != null) {
        exceptionInformation.put("exception_class", throwableName);
      }

      String throwableMessage = throwable.getThrowable().getMessage();
      if (throwableMessage != null) {
        exceptionInformation.put("exception_message", throwableMessage);
      }

      String[] stackTrace = throwable.getThrowableStrRep();
      if (stackTrace != null) {
        exceptionInformation.put("stacktrace", String.join("\n", stackTrace));
      }
      return exceptionInformation;
    }
  }
}
