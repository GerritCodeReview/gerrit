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

import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.audit.AuditEvent;
import com.google.gerrit.audit.AuditService;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.PeerDaemonUser;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.util.IdGenerator;
import com.google.gerrit.sshd.SshScope.Context;
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
import org.eclipse.jgit.util.QuotedString;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
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

  private final Provider<SshSession> session;
  private final Provider<Context> context;
  private final AsyncAppender async;
  private final AuditService auditService;

  @Inject
  SshLog(final Provider<SshSession> session, final Provider<Context> context,
      final SitePaths site, AuditService auditService) {
    this.session = session;
    this.context = context;
    this.auditService = auditService;

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
    async.append(log("LOGIN FROM " + session.get().getRemoteAddressAsString()));
    audit("0", "LOGIN", Collections.emptyList());
  }

  void onAuthFail(final SshSession sd) {
    final LoggingEvent event = new LoggingEvent( //
        Logger.class.getName(), // fqnOfCategoryClass
        null, // logger (optional)
        System.currentTimeMillis(), // when
        Level.INFO, // level
        "AUTH FAILURE FROM " + sd.getRemoteAddressAsString(), // message text
        "SSHD", // thread name
        null, // exception information
        null, // current NDC string
        null, // caller location
        null // MDC properties
        );

    event.setProperty(P_SESSION, id(sd.getSessionId()));
    event.setProperty(P_USER_NAME, sd.getUsername());

    final String error = sd.getAuthenticationError();
    if (error != null) {
      event.setProperty(P_STATUS, error);
    }

    async.append(event);
    audit("FAIL", "AUTH", Arrays.asList(sd.getRemoteAddressAsString()));
  }

  void onExecute(int exitValue) {
    final Context ctx = context.get();
    ctx.finished = System.currentTimeMillis();

    final String commandLine = ctx.getCommandLine();
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
    audit(status, getCommand(commandLine), getCommandArgs(commandLine));
  }



  private List<?> getCommandArgs(String commandLine) {
    String[] quoted = commandLine.split("'");
    if(quoted.length < 2)
      return Collections.emptyList();

    String args = quoted[1];
    return Arrays.asList(args.split(" "));
  }

  private String getCommand(String commandLine) {
    commandLine = commandLine.trim();
    int spacePos = commandLine.indexOf(' ');
    return (spacePos > 0 ? commandLine.substring(0, spacePos):commandLine);
  }

  void onLogout() {
    async.append(log("LOGOUT"));
    audit("0", "LOGOUT", Collections.emptyList());
  }

  private LoggingEvent log(final String msg) {
    final SshSession sd = session.get();
    final CurrentUser user = sd.getCurrentUser();

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

    event.setProperty(P_SESSION, id(sd.getSessionId()));

    String userName = "-", accountId = "-";

    if (user instanceof IdentifiedUser) {
      IdentifiedUser u = (IdentifiedUser) user;
      userName = u.getAccount().getUserName();
      accountId = "a/" + u.getAccountId().toString();

    } else if (user instanceof PeerDaemonUser) {
      userName = PeerDaemonUser.USER_NAME;
    }

    event.setProperty(P_USER_NAME, userName);
    event.setProperty(P_ACCOUNT_ID, accountId);

    return event;
  }

  private static String id(final int id) {
    return IdGenerator.format(id);
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

  void audit(Object result, String commandName, List<?> args) {
    final Context ctx = context.get();
    final String sid = extractSessionId(ctx);
    final String username = extractUsername(ctx);
    final long elapsed = extractElapsed(ctx);
    final long created = extractCreated(ctx);
    final String what = extractWhat(commandName, args);
    auditService.track(new AuditEvent(sid, username, "ssh:"+what, created, args, result, elapsed));
  }

  private String extractWhat(String commandName, List<?> args) {
    String result = commandName;
    if ("gerrit".equals(commandName)) {
      if (args.size() > 1)
        result = "gerrit"+"."+args.get(1);
    }
    return result;
  }

  private long extractCreated(final Context ctx) {
    return (ctx != null) ? ctx.created : System.currentTimeMillis();
  }

  private long extractElapsed(final Context ctx) {
    return (ctx != null) ? (ctx.finished - ctx.created) : 0L;
  }

  private String extractUsername(final Context ctx) {
    if (ctx != null) {
      SshSession session = ctx.getSession();
      return (session == null) ? null : session.getCurrentUser().getUserName();
    } else {
      return null;
    }
  }

  private String extractSessionId(final Context ctx) {
    if (ctx != null) {
      SshSession session = ctx.getSession();
      return (session == null) ? null : IdGenerator.format(session.getSessionId());
    } else {
      return null;
    }
  }
}
