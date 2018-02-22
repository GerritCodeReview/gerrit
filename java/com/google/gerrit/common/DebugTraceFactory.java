// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.common;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.slf4j.spi.LocationAwareLogger.DEBUG_INT;
import static org.slf4j.spi.LocationAwareLogger.ERROR_INT;
import static org.slf4j.spi.LocationAwareLogger.INFO_INT;
import static org.slf4j.spi.LocationAwareLogger.TRACE_INT;
import static org.slf4j.spi.LocationAwareLogger.WARN_INT;

import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MessageFormatter;
import org.slf4j.spi.LocationAwareLogger;

public class DebugTraceFactory {
  private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();

  public static Logger getLogger(Class<?> clazz) {
    Logger logger = LoggerFactory.getLogger(clazz);
    if (logger instanceof LocationAwareLogger) {
      return new LoggerWithTracing((LocationAwareLogger) logger);
    }
    Optional<String> traceId = getTraceId();
    if (traceId.isPresent()) {
      logger.warn("[" + traceId + "] Request tracing is not available.");
    }
    return logger;
  }

  public static boolean isTracing() {
    return TRACE_ID.get() != null;
  }

  public static Optional<String> getTraceId() {
    return Optional.ofNullable(TRACE_ID.get());
  }

  public static DebugTraceContext enableTracing(@Nullable String traceId) {
    checkState(TRACE_ID.get() == null, "trace ID already set");
    if (traceId != null) {
      validateTraceId(traceId);
    } else {
      traceId = newTraceId();
    }
    TRACE_ID.set(traceId);
    return new DebugTraceContext(traceId);
  }

  public static DebugTraceContext enableTracingForExistingTraceId(@Nullable String traceId) {
    if (traceId == null) {
      return DebugTraceContext.DISABLED_DEBUG_TRACE;
    }

    checkState(TRACE_ID.get() == null, "trace ID already set");
    validateTraceId(traceId);
    TRACE_ID.set(traceId);
    return new DebugTraceContext(traceId);
  }

  private static String newTraceId() {
    return UUID.randomUUID().toString();
  }

  public static void validateTraceId(String traceId) throws IllegalArgumentException {
    UUID.fromString(traceId);
  }

  public static class DebugTraceContext implements AutoCloseable {
    public static final DebugTraceContext DISABLED_DEBUG_TRACE = new DebugTraceContext(null);

    private final Optional<String> traceId;

    DebugTraceContext(@Nullable String traceId) {
      this.traceId = Optional.ofNullable(traceId);
    }

    public boolean isTracing() {
      return traceId.isPresent();
    }

    public Optional<String> getTraceId() {
      return traceId;
    }

    @Override
    public void close() {
      if (traceId.isPresent()) {
        TRACE_ID.remove();
      }
    }
  }

  private static class LoggerWithTracing implements Logger {
    private final LocationAwareLogger logger;

    LoggerWithTracing(LocationAwareLogger logger) {
      this.logger = checkNotNull(logger, "logger");
    }

    @Override
    public String getName() {
      return logger.getName();
    }

    @Override
    public boolean isTraceEnabled() {
      return isTraceEnabled(null);
    }

    @Override
    public boolean isTraceEnabled(Marker marker) {
      if (isTracing()) {
        return logger.isDebugEnabled(marker);
      }
      return logger.isTraceEnabled(marker);
    }

    @Override
    public boolean isDebugEnabled() {
      return isDebugEnabled(null);
    }

    @Override
    public boolean isDebugEnabled(Marker marker) {
      if (isTracing()) {
        return logger.isInfoEnabled(marker);
      }
      return logger.isDebugEnabled(marker);
    }

    @Override
    public boolean isInfoEnabled() {
      return logger.isInfoEnabled();
    }

    @Override
    public boolean isInfoEnabled(Marker marker) {
      return logger.isInfoEnabled(marker);
    }

    @Override
    public boolean isWarnEnabled() {
      return logger.isWarnEnabled();
    }

    @Override
    public boolean isWarnEnabled(Marker marker) {
      return logger.isWarnEnabled(marker);
    }

    @Override
    public boolean isErrorEnabled() {
      return logger.isErrorEnabled();
    }

    @Override
    public boolean isErrorEnabled(Marker marker) {
      return logger.isErrorEnabled(marker);
    }

    @Override
    public void trace(String msg) {
      log(TRACE_INT, msg);
    }

    @Override
    public void trace(String format, Object arg) {
      log(TRACE_INT, format, arg);
    }

    @Override
    public void trace(String format, Object arg1, Object arg2) {
      log(TRACE_INT, format, arg1, arg2);
    }

    @Override
    public void trace(String format, Object... arguments) {
      log(TRACE_INT, format, arguments);
    }

    @Override
    public void trace(String msg, Throwable t) {
      log(TRACE_INT, msg, t);
    }

    @Override
    public void trace(Marker marker, String msg) {
      log(marker, TRACE_INT, msg);
    }

    @Override
    public void trace(Marker marker, String format, Object arg) {
      log(marker, TRACE_INT, format, arg);
    }

    @Override
    public void trace(Marker marker, String format, Object arg1, Object arg2) {
      log(marker, TRACE_INT, format, arg1, arg2);
    }

    @Override
    public void trace(Marker marker, String format, Object... arguments) {
      log(marker, TRACE_INT, format, arguments);
    }

    @Override
    public void trace(Marker marker, String msg, Throwable t) {
      log(marker, TRACE_INT, msg, t);
    }

    @Override
    public void debug(String msg) {
      log(DEBUG_INT, msg);
    }

    @Override
    public void debug(String format, Object arg) {
      log(DEBUG_INT, format, arg);
    }

    @Override
    public void debug(String format, Object arg1, Object arg2) {
      log(DEBUG_INT, format, arg1, arg2);
    }

    @Override
    public void debug(String format, Object... arguments) {
      log(DEBUG_INT, format, arguments);
    }

    @Override
    public void debug(String msg, Throwable t) {
      log(DEBUG_INT, msg, t);
    }

    @Override
    public void debug(Marker marker, String msg) {
      log(marker, DEBUG_INT, msg);
    }

    @Override
    public void debug(Marker marker, String format, Object arg) {
      log(marker, DEBUG_INT, format, arg);
    }

    @Override
    public void debug(Marker marker, String format, Object arg1, Object arg2) {
      log(marker, DEBUG_INT, format, arg1, arg2);
    }

    @Override
    public void debug(Marker marker, String format, Object... arguments) {
      log(marker, DEBUG_INT, format, arguments);
    }

    @Override
    public void debug(Marker marker, String msg, Throwable t) {
      log(marker, DEBUG_INT, msg, t);
    }

    @Override
    public void info(String msg) {
      log(INFO_INT, msg);
    }

    @Override
    public void info(String format, Object arg) {
      log(INFO_INT, format, arg);
    }

    @Override
    public void info(String format, Object arg1, Object arg2) {
      log(INFO_INT, format, arg1, arg2);
    }

    @Override
    public void info(String format, Object... arguments) {
      log(INFO_INT, format, arguments);
    }

    @Override
    public void info(String msg, Throwable t) {
      log(INFO_INT, msg, t);
    }

    @Override
    public void info(Marker marker, String msg) {
      log(marker, INFO_INT, msg);
    }

    @Override
    public void info(Marker marker, String format, Object arg) {
      log(marker, INFO_INT, format, arg);
    }

    @Override
    public void info(Marker marker, String format, Object arg1, Object arg2) {
      log(marker, INFO_INT, format, arg1, arg2);
    }

    @Override
    public void info(Marker marker, String format, Object... arguments) {
      log(marker, INFO_INT, format, arguments);
    }

    @Override
    public void info(Marker marker, String msg, Throwable t) {
      log(marker, INFO_INT, msg, t);
    }

    @Override
    public void warn(String msg) {
      log(WARN_INT, msg);
    }

    @Override
    public void warn(String format, Object arg) {
      log(WARN_INT, format, arg);
    }

    @Override
    public void warn(String format, Object arg1, Object arg2) {
      log(WARN_INT, format, arg1, arg2);
    }

    @Override
    public void warn(String format, Object... arguments) {
      log(WARN_INT, format, arguments);
    }

    @Override
    public void warn(String msg, Throwable t) {
      log(WARN_INT, msg, t);
    }

    @Override
    public void warn(Marker marker, String msg) {
      log(marker, WARN_INT, msg);
    }

    @Override
    public void warn(Marker marker, String format, Object arg) {
      log(marker, WARN_INT, format, arg);
    }

    @Override
    public void warn(Marker marker, String format, Object arg1, Object arg2) {
      log(marker, WARN_INT, format, arg1, arg2);
    }

    @Override
    public void warn(Marker marker, String format, Object... arguments) {
      log(marker, WARN_INT, format, arguments);
    }

    @Override
    public void warn(Marker marker, String msg, Throwable t) {
      log(marker, WARN_INT, msg, t);
    }

    @Override
    public void error(String msg) {
      log(ERROR_INT, msg);
    }

    @Override
    public void error(String format, Object arg) {
      log(ERROR_INT, format, arg);
    }

    @Override
    public void error(String format, Object arg1, Object arg2) {
      log(ERROR_INT, format, arg1, arg2);
    }

    @Override
    public void error(String format, Object... arguments) {
      log(ERROR_INT, format, arguments);
    }

    @Override
    public void error(String msg, Throwable t) {
      log(ERROR_INT, msg, t);
    }

    @Override
    public void error(Marker marker, String msg) {
      log(marker, ERROR_INT, msg);
    }

    @Override
    public void error(Marker marker, String format, Object arg) {
      log(marker, ERROR_INT, format, arg);
    }

    @Override
    public void error(Marker marker, String format, Object arg1, Object arg2) {
      log(marker, ERROR_INT, format, arg1, arg2);
    }

    @Override
    public void error(Marker marker, String format, Object... arguments) {
      log(marker, ERROR_INT, format, arguments);
    }

    @Override
    public void error(Marker marker, String msg, Throwable t) {
      log(marker, ERROR_INT, msg, t);
    }

    private void log(int level, String msg) {
      log(null, level, msg);
    }

    private void log(int level, String format, Object arg) {
      log(null, level, format, arg);
    }

    private void log(int level, String format, Object arg1, Object arg2) {
      log(null, level, format, arg1, arg2);
    }

    private void log(int level, String format, Object... arguments) {
      log(null, level, format, arguments);
    }

    private void log(int level, String msg, Throwable t) {
      log(null, level, msg, t);
    }

    private void log(Marker marker, int level, String msg) {
      log(marker, level, msg, (Throwable) null);
    }

    private void log(Marker marker, int level, String format, Object arg) {
      FormattingTuple ft = MessageFormatter.format(format, arg);
      log(marker, level, ft.getMessage(), ft.getThrowable());
    }

    private void log(Marker marker, int level, String format, Object arg1, Object arg2) {
      FormattingTuple ft = MessageFormatter.format(format, arg1, arg2);
      log(marker, level, ft.getMessage(), ft.getThrowable());
    }

    private void log(Marker marker, int level, String format, Object... arguments) {
      FormattingTuple ft = MessageFormatter.arrayFormat(format, arguments);
      log(marker, level, ft.getMessage(), ft.getThrowable());
    }

    private void log(@Nullable Marker marker, int level, String msg, @Nullable Throwable t) {
      if (isTracing()) {
        msg = withTraceId(msg);

        if (level == TRACE_INT) {
          level = DEBUG_INT;
        } else if (level == DEBUG_INT) {
          level = INFO_INT;
        }
      }

      logger.log(
          marker,
          DebugTraceFactory.LoggerWithTracing.class.getName(),
          level,
          msg,
          new Object[0],
          t);
    }

    private String withTraceId(String s) {
      String traceId = TRACE_ID.get();
      if (traceId == null) {
        return s;
      }
      return "[" + traceId + "] " + s;
    }
  }
}
