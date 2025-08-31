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
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.impl.ThrowableProxy;

/** Layout for formatting error log events in the JSON format. */
public class ErrorLogJsonLayout extends JsonLayout {
  public ErrorLogJsonLayout() {
    super(Charset.defaultCharset()); // ✅ required constructor in Log4j2
  }

  @Override
  public JsonLogEntry toJsonLogEntry(LogEvent event) {
    return new ErrorJsonLogEntry(event);
  }

  @SuppressWarnings("unused")
  private class ErrorJsonLogEntry extends JsonLogEntry {
    @SerializedName("@timestamp")
    public final String timestamp;

    public final String sourceHost;
    public final String message;
    public final String file;
    public final String lineNumber;

    @SerializedName("class")
    public final String clazz;

    public final String method;
    public final String loggerName;
    public final Map<String, String> mdc;
    public final String ndc;
    public final String level;
    public final String threadName;

    @SerializedName("@version")
    public final int version = 2;

    public Map<String, String> exception;

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
