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
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.impl.ThrowableProxy;

/** Layout for formatting error log events in the JSON format. */
public class ErrorLogJsonLayout extends JsonLayout {
  public ErrorLogJsonLayout() {
    super(StandardCharsets.UTF_8);
  }

  @Override
  public JsonLogEntry toJsonLogEntry(LogEvent event) {
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
    public final Map<String, String> mdc;

    /** Nested diagnostic context. */
    public final String ndc;

    /** Logging level/severity. */
    public final String level;

    /** Thread executing the code creating the log entry. */
    public final String threadName;

    @SerializedName("@version")
    public final int version = 2;

    public Map<String, String> exception;

    /**
     * Map containing information of a logged exception. It contains the following key-value pairs:
     * exception_class: Which class threw the exception exception_method: Which method threw the
     * exception stacktrace: The exception stacktrace
     */
    public ErrorJsonLogEntry(LogEvent event) {
      this.timestamp = timestampFormatter.format(event.getTimeMillis());
      this.sourceHost = getSourceHost();
      this.message = event.getMessage().getFormattedMessage();
      this.file = (event.getSource() != null) ? event.getSource().getFileName() : null;
      this.lineNumber =
          (event.getSource() != null) ? String.valueOf(event.getSource().getLineNumber()) : null;
      this.clazz = (event.getSource() != null) ? event.getSource().getClassName() : null;
      this.method = (event.getSource() != null) ? event.getSource().getMethodName() : null;
      this.loggerName = event.getLoggerName();
      this.mdc = event.getContextData().toMap();
      this.ndc = event.getContextStack().toString(); // ✅ replaced asString()
      this.level = event.getLevel().toString();
      this.threadName = event.getThreadName();
      if (event.getThrownProxy() != null) {
        this.exception = getException(event.getThrownProxy());
      }
    }

    private String getSourceHost() {
      try {
        return InetAddress.getLocalHost().getHostAddress();
      } catch (UnknownHostException e) {
        return "unknown-host";
      }
    }

    private Map<String, String> getException(ThrowableProxy proxy) {
      HashMap<String, String> exceptionInformation = new HashMap<>();

      Throwable t = proxy.getThrowable();
      if (t != null) {
        if (t.getClass().getCanonicalName() != null) {
          exceptionInformation.put("exception_class", t.getClass().getCanonicalName());
        }
        if (t.getMessage() != null) {
          exceptionInformation.put("exception_message", t.getMessage());
        }
      }

      String stackTrace = proxy.getExtendedStackTraceAsString();
      if (stackTrace != null) {
        exceptionInformation.put("stacktrace", stackTrace);
      }
      return exceptionInformation;
    }
  }
}
