// Copyright (C) 2009 The Android Open Source Project
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

import com.google.gerrit.lifecycle.LifecycleListener;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.util.IdGenerator;
import com.google.gerrit.sshd.SshScopes.Context;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.apache.log4j.Appender;
import org.apache.log4j.AsyncAppender;
import org.apache.log4j.DailyRollingFileAppender;
import org.apache.log4j.Layout;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.ErrorHandler;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.sshd.server.session.ServerSession;
import org.eclipse.jgit.util.QuotedString;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

@Singleton
class SshLog implements LifecycleListener {
  private static final Logger log = Logger.getLogger(SshLog.class);
  private static final String LOG_NAME = "sshd_log";
  private static final String P_SESSION = "session";
  private static final String P_USER_NAME = "userName";
  private static final String P_ACCOUNT_ID = "accountId";
  private static final String P_WAIT = "queueWaitTime";
  private static final String P_EXEC = "executionTime";
  private static final String P_STATUS = "status";

  private final Provider<ServerSession> session;
  private final Provider<IdentifiedUser> user;
  private final AsyncAppender async;

  @Inject
  SshLog(final Provider<ServerSession> session,
      final Provider<IdentifiedUser> user, final SitePaths site) {
    this.session = session;
    this.user = user;

    final DailyRollingFileAppender dst = new DailyRollingFileAppender();
    dst.setName(LOG_NAME);
    dst.setLayout(new MyLayout());
    dst.setEncoding("UTF-8");
    dst.setFile(new File(resolve(site.logs_dir), LOG_NAME).getPath());
    dst.setImmediateFlush(true);
    dst.setAppend(true);
    dst.setThreshold(Level.INFO);
    dst.setErrorHandler(new DieErrorHandler());
    dst.activateOptions();
    dst.setErrorHandler(new LogLogHandler());

    async = new AsyncAppender();
    async.setBlocking(true);
    async.setBufferSize(64);
    async.setLocationInfo(false);
    async.addAppender(dst);
    async.activateOptions();
  }

  @Override
  public void start() {
  }

  @Override
  public void stop() {
    async.close();
  }

  void onLogin() {
    final ServerSession s = session.get();
    final SocketAddress addr = s.getIoSession().getRemoteAddress();
    async.append(log("LOGIN FROM " + format(addr)));
  }

  void onAuthFail(final ServerSession s, final String username) {
    final SocketAddress addr = s.getIoSession().getRemoteAddress();

    final LoggingEvent event = new LoggingEvent( //
        Logger.class.getName(), // fqnOfCategoryClass
        null, // logger (optional)
        System.currentTimeMillis(), // when
        Level.INFO, // level
        "AUTH FAILURE FROM " + format(addr), // message text
        "SSHD", // thread name
        null, // exception information
        null, // current NDC string
        null, // caller location
        null // MDC properties
        );

    event.setProperty(P_SESSION, id(s.getAttribute(SshUtil.SESSION_ID)));
    event.setProperty(P_USER_NAME, username);

    final String error = s.getAttribute(SshUtil.AUTH_ERROR);
    if (error != null) {
      event.setProperty(P_STATUS, error);
    }

    async.append(event);
  }

  void onExecute(final Context ctx, final String commandLine, int exitValue) {
    String cmd = QuotedString.BOURNE.quote(commandLine);
    if (cmd == commandLine) {
      cmd = "'" + commandLine + "'";
    }

    final LoggingEvent event = log(cmd);
    event.setProperty(P_WAIT, (ctx.started - ctx.created) + "ms");
    event.setProperty(P_EXEC, (ctx.finished - ctx.started) + "ms");

    final String status;
    switch (exitValue) {
      case BaseCommand.STATUS_CANCEL:
        status = "killed";
        break;

      case BaseCommand.STATUS_NOT_FOUND:
        status = "not-found";
        break;

      case BaseCommand.STATUS_NOT_ADMIN:
        status = "not-admin";
        break;

      default:
        status = String.valueOf(exitValue);
        break;
    }
    event.setProperty(P_STATUS, status);

    async.append(event);
  }

  void onLogout() {
    async.append(log("LOGOUT"));
  }

  private LoggingEvent log(final String msg) {
    final ServerSession s = session.get();
    final IdentifiedUser u = user.get();

    final LoggingEvent event = new LoggingEvent( //
        Logger.class.getName(), // fqnOfCategoryClass
        null, // logger (optional)
        System.currentTimeMillis(), // when
        Level.INFO, // level
        msg, // message text
        "SSHD", // thread name
        null, // exception information
        null, // current NDC string
        null, // caller location
        null // MDC properties
        );

    event.setProperty(P_SESSION, id(s.getAttribute(SshUtil.SESSION_ID)));
    event.setProperty(P_USER_NAME, u.getAccount().getSshUserName());
    event.setProperty(P_ACCOUNT_ID, "a/" + u.getAccountId().toString());

    return event;
  }

  private static String format(final SocketAddress remote) {
    if (remote instanceof InetSocketAddress) {
      final InetSocketAddress sa = (InetSocketAddress) remote;

      final InetAddress in = sa.getAddress();
      if (in != null) {
        return in.getHostAddress();
      }

      final String hostName = sa.getHostName();
      if (hostName != null) {
        return hostName;
      }
    }
    return remote.toString();
  }

  private static String id(final Integer id) {
    return id != null ? IdGenerator.format(id) : "";
  }

  private static File resolve(final File logs_dir) {
    try {
      return logs_dir.getCanonicalFile();
    } catch (IOException e) {
      return logs_dir.getAbsoluteFile();
    }
  }

  private static final class MyLayout extends Layout {
    private final Calendar calendar;
    private long lastTimeMillis;
    private final char[] lastTimeString = new char[20];
    private final char[] timeZone;

    MyLayout() {
      final TimeZone tz = TimeZone.getDefault();
      calendar = Calendar.getInstance(tz);

      final SimpleDateFormat sdf = new SimpleDateFormat("Z");
      sdf.setTimeZone(tz);
      timeZone = sdf.format(new Date()).toCharArray();
    }

    @Override
    public String format(LoggingEvent event) {
      final StringBuffer buf = new StringBuffer(128);

      buf.append('[');
      formatDate(event.getTimeStamp(), buf);
      buf.append(' ');
      buf.append(timeZone);
      buf.append(']');

      req(P_SESSION, buf, event);
      req(P_USER_NAME, buf, event);
      req(P_ACCOUNT_ID, buf, event);

      buf.append(' ');
      buf.append(event.getMessage());

      opt(P_WAIT, buf, event);
      opt(P_EXEC, buf, event);
      opt(P_STATUS, buf, event);

      buf.append('\n');
      return buf.toString();
    }

    private void formatDate(final long now, final StringBuffer sbuf) {
      final int millis = (int) (now % 1000);
      final long rounded = now - millis;
      if (rounded != lastTimeMillis) {
        synchronized (calendar) {
          final int start = sbuf.length();

          calendar.setTimeInMillis(rounded);
          sbuf.append(calendar.get(Calendar.YEAR));
          sbuf.append('-');
          final int month = calendar.get(Calendar.MONTH) + 1;
          if (month < 10) sbuf.append('0');
          sbuf.append(month);
          sbuf.append('-');
          final int day = calendar.get(Calendar.DAY_OF_MONTH);
          if (day < 10) sbuf.append('0');
          sbuf.append(day);

          sbuf.append(' ');
          final int hour = calendar.get(Calendar.HOUR_OF_DAY);
          if (hour < 10) sbuf.append('0');
          sbuf.append(hour);
          sbuf.append(':');
          final int mins = calendar.get(Calendar.MINUTE);
          if (mins < 10) sbuf.append('0');
          sbuf.append(mins);
          sbuf.append(':');
          final int secs = calendar.get(Calendar.SECOND);
          if (secs < 10) sbuf.append('0');
          sbuf.append(secs);

          sbuf.append(',');
          sbuf.getChars(start, sbuf.length(), lastTimeString, 0);
          lastTimeMillis = rounded;
        }
      } else {
        sbuf.append(lastTimeString);
      }
      if (millis < 100) {
        sbuf.append('0');
      }
      if (millis < 10) {
        sbuf.append('0');
      }
      sbuf.append(millis);
    }

    private void req(String key, StringBuffer buf, LoggingEvent event) {
      Object val = event.getMDC(key);
      buf.append(' ');
      if (val != null) {
        String s = val.toString();
        if (0 <= s.indexOf(' ')) {
          buf.append(QuotedString.BOURNE.quote(s));
        } else {
          buf.append(val);
        }
      } else {
        buf.append('-');
      }
    }

    private void opt(String key, StringBuffer buf, LoggingEvent event) {
      Object val = event.getMDC(key);
      if (val != null) {
        buf.append(' ');
        buf.append(val);
      }
    }

    @Override
    public boolean ignoresThrowable() {
      return true;
    }

    @Override
    public void activateOptions() {
    }
  }

  private static final class DieErrorHandler implements ErrorHandler {
    @Override
    public void error(String message, Exception e, int errorCode,
        LoggingEvent event) {
      error(e != null ? e.getMessage() : message);
    }

    @Override
    public void error(String message, Exception e, int errorCode) {
      error(e != null ? e.getMessage() : message);
    }

    @Override
    public void error(String message) {
      throw new RuntimeException("Cannot open log file: " + message);
    }

    @Override
    public void activateOptions() {
    }

    @Override
    public void setAppender(Appender appender) {
    }

    @Override
    public void setBackupAppender(Appender appender) {
    }

    @Override
    public void setLogger(Logger logger) {
    }
  }

  private static final class LogLogHandler implements ErrorHandler {
    @Override
    public void error(String message, Exception e, int errorCode,
        LoggingEvent event) {
      log.error(message, e);
    }

    @Override
    public void error(String message, Exception e, int errorCode) {
      log.error(message, e);
    }

    @Override
    public void error(String message) {
      log.error(message);
    }

    @Override
    public void activateOptions() {
    }

    @Override
    public void setAppender(Appender appender) {
    }

    @Override
    public void setBackupAppender(Appender appender) {
    }

    @Override
    public void setLogger(Logger logger) {
    }
  }
}
