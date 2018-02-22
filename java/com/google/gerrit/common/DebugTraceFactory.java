package com.google.gerrit.common;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;

public class DebugTraceFactory {
  private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();

  public static Logger getLogger(Class<?> clazz) {
    return new LoggerWithTracing(LoggerFactory.getLogger(clazz));
  }

  public static boolean isTracing() {
    return TRACE_ID.get() != null;
  }

  public static Optional<String> getTraceId() {
    return Optional.ofNullable(TRACE_ID.get());
  }

  public static String enableTracing(@Nullable String traceId) {
    checkState(TRACE_ID.get() == null, "trace ID already set");
    if (traceId != null) {
      // TODO validate traceId format
    } else {
      traceId = newTraceId();
    }
    TRACE_ID.set(traceId);
    return traceId;
  }

  private static String newTraceId() {
    return UUID.randomUUID().toString();
  }

  private static class LoggerWithTracing implements Logger {
    private final Logger logger;

    LoggerWithTracing(Logger logger) {
      this.logger = checkNotNull(logger, "logger");
    }

    @Override
    public String getName() {
      return logger.getName();
    }

    @Override
    public boolean isTraceEnabled() {
      if (isTracing()) {
        return logger.isDebugEnabled();
      }
      return logger.isTraceEnabled();
    }

    @Override
    public void trace(String msg) {
      if (isTracing()) {
        logger.debug(withTraceId(msg));
        return;
      }
      logger.trace(msg);
    }

    @Override
    public void trace(String format, Object arg) {
      if (isTracing()) {
        logger.debug(withTraceId(format), arg);
        return;
      }
      logger.trace(format, arg);
    }

    @Override
    public void trace(String format, Object arg1, Object arg2) {
      if (isTracing()) {
        logger.debug(withTraceId(format), arg1, arg2);
        return;
      }
      logger.trace(format, arg1, arg2);
    }

    @Override
    public void trace(String format, Object... arguments) {
      if (isTracing()) {
        logger.debug(withTraceId(format), arguments);
        return;
      }
      logger.trace(format, arguments);
    }

    @Override
    public void trace(String msg, Throwable t) {
      if (isTracing()) {
        logger.debug(withTraceId(msg), t);
        return;
      }
      logger.trace(msg, t);
    }

    @Override
    public boolean isTraceEnabled(Marker marker) {
      if (isTracing()) {
        return logger.isDebugEnabled(marker);
      }
      return logger.isTraceEnabled(marker);
    }

    @Override
    public void trace(Marker marker, String msg) {
      if (isTracing()) {
        logger.debug(marker, withTraceId(msg));
        return;
      }
      logger.trace(marker, msg);
    }

    @Override
    public void trace(Marker marker, String format, Object arg) {
      if (isTracing()) {
        logger.debug(marker, withTraceId(format), arg);
        return;
      }
      logger.trace(marker, format, arg);
    }

    @Override
    public void trace(Marker marker, String format, Object arg1, Object arg2) {
      if (isTracing()) {
        logger.debug(marker, withTraceId(format), arg1, arg2);
        return;
      }
      logger.trace(marker, format, arg1, arg2);
    }

    @Override
    public void trace(Marker marker, String format, Object... argArray) {
      if (isTracing()) {
        logger.debug(marker, withTraceId(format), argArray);
        return;
      }
      logger.trace(marker, format, argArray);
    }

    @Override
    public void trace(Marker marker, String msg, Throwable t) {
      if (isTracing()) {
        logger.debug(marker, withTraceId(msg), t);
        return;
      }
      logger.trace(marker, msg, t);
    }

    @Override
    public boolean isDebugEnabled() {
      if (isTracing()) {
        return logger.isInfoEnabled();
      }
      return logger.isDebugEnabled();
    }

    @Override
    public void debug(String msg) {
      if (isTracing()) {
        logger.info(withTraceId(msg));
        return;
      }
      logger.debug(msg);
    }

    @Override
    public void debug(String format, Object arg) {
      if (isTracing()) {
        logger.info(withTraceId(format), arg);
        return;
      }
      logger.debug(format, arg);
    }

    @Override
    public void debug(String format, Object arg1, Object arg2) {
      if (isTracing()) {
        logger.info(withTraceId(format), arg1, arg2);
        return;
      }
      logger.debug(format, arg1, arg2);
    }

    @Override
    public void debug(String format, Object... arguments) {
      if (isTracing()) {
        logger.info(withTraceId(format), arguments);
        return;
      }
      logger.debug(format, arguments);
    }

    @Override
    public void debug(String msg, Throwable t) {
      if (isTracing()) {
        logger.info(withTraceId(msg), t);
        return;
      }
      logger.debug(msg, t);
    }

    @Override
    public boolean isDebugEnabled(Marker marker) {
      if (isTracing()) {
        return logger.isInfoEnabled(marker);
      }
      return logger.isDebugEnabled(marker);
    }

    @Override
    public void debug(Marker marker, String msg) {
      if (isTracing()) {
        logger.info(marker, withTraceId(msg));
        return;
      }
      logger.debug(marker, msg);
    }

    @Override
    public void debug(Marker marker, String format, Object arg) {
      if (isTracing()) {
        logger.info(marker, withTraceId(format), arg);
        return;
      }
      logger.debug(marker, format, arg);
    }

    @Override
    public void debug(Marker marker, String format, Object arg1, Object arg2) {
      if (isTracing()) {
        logger.info(marker, withTraceId(format), arg1, arg2);
        return;
      }
      logger.debug(marker, format, arg1, arg2);
    }

    @Override
    public void debug(Marker marker, String format, Object... arguments) {
      if (isTracing()) {
        logger.info(marker, withTraceId(format), arguments);
        return;
      }
      logger.debug(marker, format, arguments);
    }

    @Override
    public void debug(Marker marker, String msg, Throwable t) {
      if (isTracing()) {
        logger.info(marker, withTraceId(msg), t);
        return;
      }
      logger.debug(marker, msg, t);
    }

    @Override
    public boolean isInfoEnabled() {
      return logger.isInfoEnabled();
    }

    @Override
    public void info(String msg) {
      logger.info(withTraceId(msg));
    }

    @Override
    public void info(String format, Object arg) {
      logger.info(withTraceId(format), arg);
    }

    @Override
    public void info(String format, Object arg1, Object arg2) {
      logger.info(withTraceId(format), arg1, arg2);
    }

    @Override
    public void info(String format, Object... arguments) {
      logger.info(withTraceId(format), arguments);
    }

    @Override
    public void info(String msg, Throwable t) {
      logger.info(withTraceId(msg), t);
    }

    @Override
    public boolean isInfoEnabled(Marker marker) {
      return logger.isInfoEnabled(marker);
    }

    @Override
    public void info(Marker marker, String msg) {
      logger.info(marker, withTraceId(msg));
    }

    @Override
    public void info(Marker marker, String format, Object arg) {
      logger.info(marker, withTraceId(format), arg);
    }

    @Override
    public void info(Marker marker, String format, Object arg1, Object arg2) {
      logger.info(marker, withTraceId(format), arg1, arg2);
    }

    @Override
    public void info(Marker marker, String format, Object... arguments) {
      logger.info(marker, withTraceId(format), arguments);
    }

    @Override
    public void info(Marker marker, String msg, Throwable t) {
      logger.info(marker, withTraceId(msg), t);
    }

    @Override
    public boolean isWarnEnabled() {
      return logger.isWarnEnabled();
    }

    @Override
    public void warn(String msg) {
      logger.warn(withTraceId(msg));
    }

    @Override
    public void warn(String format, Object arg) {
      logger.warn(withTraceId(format), arg);
    }

    @Override
    public void warn(String format, Object... arguments) {
      logger.warn(withTraceId(format), arguments);
    }

    @Override
    public void warn(String format, Object arg1, Object arg2) {
      logger.warn(withTraceId(format), arg1, arg2);
    }

    @Override
    public void warn(String msg, Throwable t) {
      logger.warn(withTraceId(msg), t);
    }

    @Override
    public boolean isWarnEnabled(Marker marker) {
      return logger.isWarnEnabled(marker);
    }

    @Override
    public void warn(Marker marker, String msg) {
      logger.warn(marker, withTraceId(msg));
    }

    @Override
    public void warn(Marker marker, String format, Object arg) {
      logger.warn(marker, withTraceId(format), arg);
    }

    @Override
    public void warn(Marker marker, String format, Object arg1, Object arg2) {
      logger.warn(marker, withTraceId(format), arg1, arg2);
    }

    @Override
    public void warn(Marker marker, String format, Object... arguments) {
      logger.warn(marker, withTraceId(format), arguments);
    }

    @Override
    public void warn(Marker marker, String msg, Throwable t) {
      logger.warn(marker, withTraceId(msg), t);
    }

    @Override
    public boolean isErrorEnabled() {
      return logger.isErrorEnabled();
    }

    @Override
    public void error(String msg) {
      logger.error(withTraceId(msg));
    }

    @Override
    public void error(String format, Object arg) {
      logger.error(withTraceId(format), arg);
    }

    @Override
    public void error(String format, Object arg1, Object arg2) {
      logger.error(withTraceId(format), arg1, arg2);
    }

    @Override
    public void error(String format, Object... arguments) {
      logger.error(withTraceId(format), arguments);
    }

    @Override
    public void error(String msg, Throwable t) {
      logger.error(withTraceId(msg), t);
    }

    @Override
    public boolean isErrorEnabled(Marker marker) {
      return logger.isErrorEnabled(marker);
    }

    @Override
    public void error(Marker marker, String msg) {
      logger.error(marker, withTraceId(msg));
    }

    @Override
    public void error(Marker marker, String format, Object arg) {
      logger.error(marker, withTraceId(format), arg);
    }

    @Override
    public void error(Marker marker, String format, Object arg1, Object arg2) {
      logger.error(marker, withTraceId(format), arg1, arg2);
    }

    @Override
    public void error(Marker marker, String format, Object... arguments) {
      logger.error(marker, withTraceId(format), arguments);
    }

    @Override
    public void error(Marker marker, String msg, Throwable t) {
      logger.error(marker, withTraceId(msg), t);
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
