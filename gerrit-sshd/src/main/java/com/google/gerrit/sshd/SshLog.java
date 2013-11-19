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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.gerrit.audit.AuditService;
import com.google.gerrit.audit.SshAuditEvent;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.PeerDaemonUser;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.util.IdGenerator;
import com.google.gerrit.server.util.LogUtil;
import com.google.gerrit.server.util.TimeUtil;
import com.google.gerrit.sshd.SshScope.Context;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.apache.log4j.Appender;
import org.apache.log4j.AsyncAppender;
import org.apache.log4j.DailyRollingFileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.ErrorHandler;
import org.apache.log4j.spi.LoggingEvent;
import org.eclipse.jgit.lib.Config;

import java.io.File;
import java.io.IOException;

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
      final SitePaths site, @GerritServerConfig Config config, AuditService auditService) {
    this.session = session;
    this.context = context;
    this.auditService = auditService;

    async = new AsyncAppender();
    async.setBlocking(true);
    async.setBufferSize(config.getInt("core", "asyncLoggingBufferSize", 64));
    async.setLocationInfo(false);

    if (LogUtil.shouldConfigureLogSystem()) {
      final DailyRollingFileAppender dst = new DailyRollingFileAppender();
      dst.setName(LOG_NAME);
      dst.setLayout(new SshLogLayout());
      dst.setEncoding("UTF-8");
      dst.setFile(new File(resolve(site.logs_dir), LOG_NAME).getPath());
      dst.setImmediateFlush(true);
      dst.setAppend(true);
      dst.setThreshold(Level.INFO);
      dst.setErrorHandler(new DieErrorHandler());
      dst.activateOptions();
      dst.setErrorHandler(new LogLogHandler());
      async.addAppender(dst);
    } else {
      Appender appender = log.getAppender(LOG_NAME);
      if (appender != null) {
        async.addAppender(appender);
      } else {
        log.warn("No appender with the name: "
            + LOG_NAME
            + " was found. SSHD logging is disabled");
      }
    }
    async.activateOptions();
  }

  @Override
  public void start() {
  }

  @Override
  public void stop() {
    if (async != null) {
      async.close();
    }
  }

  void onLogin() {
    LoggingEvent entry =
        log("LOGIN FROM " + session.get().getRemoteAddressAsString());
    if (async != null) {
      async.append(entry);
    }
    audit(context.get(), "0", "LOGIN");
  }

  void onAuthFail(final SshSession sd) {
    final LoggingEvent event = new LoggingEvent( //
        Logger.class.getName(), // fqnOfCategoryClass
        log, // logger
        TimeUtil.nowMs(), // when
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
    if (async != null) {
      async.append(event);
    }
    audit(null, "FAIL", "AUTH");
  }

  void onExecute(DispatchCommand dcmd, int exitValue) {
    final Context ctx = context.get();
    ctx.finished = TimeUtil.nowMs();

    String cmd = extractWhat(dcmd);

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

    if (async != null) {
      async.append(event);
    }
    audit(context.get(), status, dcmd);
  }

  private Multimap<String, ?> extractParameters(DispatchCommand dcmd) {
    String[] cmdArgs = dcmd.getArguments();
    String paramName = null;
    int argPos = 0;
    Multimap<String, String> parms = ArrayListMultimap.create();
    for (int i = 2; i < cmdArgs.length; i++) {
      String arg = cmdArgs[i];
      // -- stop parameters parsing
      if (arg.equals("--")) {
        for (i++; i < cmdArgs.length; i++) {
          parms.put("$" + argPos++, cmdArgs[i]);
        }
        break;
      }
      // --param=value
      int eqPos = arg.indexOf('=');
      if (arg.startsWith("--") && eqPos > 0) {
        parms.put(arg.substring(0, eqPos), arg.substring(eqPos + 1));
        continue;
      }
      // -p value or --param value
      if (arg.startsWith("-")) {
        if (paramName != null) {
          parms.put(paramName, null);
        }
        paramName = arg;
        continue;
      }
      // value
      if (paramName == null) {
        parms.put("$" + argPos++, arg);
      } else {
        parms.put(paramName, arg);
        paramName = null;
      }
    }
    if (paramName != null) {
      parms.put(paramName, null);
    }
    return parms;
  }

  void onLogout() {
    LoggingEvent entry = log("LOGOUT");
    if (async != null) {
      async.append(entry);
    }
    audit(context.get(), "0", "LOGOUT");
  }

  private LoggingEvent log(final String msg) {
    final SshSession sd = session.get();
    final CurrentUser user = sd.getCurrentUser();

    final LoggingEvent event = new LoggingEvent( //
        Logger.class.getName(), // fqnOfCategoryClass
        log, // logger
        TimeUtil.nowMs(), // when
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

    if (user != null && user.isIdentifiedUser()) {
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

  void audit(Context ctx, Object result, String cmd) {
    final String sid = extractSessionId(ctx);
    final long created = extractCreated(ctx);
    auditService.dispatch(new SshAuditEvent(sid, extractCurrentUser(ctx), cmd,
        created, null, result));
  }

  void audit(Context ctx, Object result, DispatchCommand cmd) {
    final String sid = extractSessionId(ctx);
    final long created = extractCreated(ctx);
    auditService.dispatch(new SshAuditEvent(sid, extractCurrentUser(ctx),
        extractWhat(cmd), created, extractParameters(cmd), result));
  }

  private String extractWhat(DispatchCommand dcmd) {
    String commandName = dcmd.getCommandName();
    String[] args = dcmd.getArguments();
    if (args.length > 1) {
      return commandName + "." + args[1];
    } else {
      return commandName;
    }
  }

  private long extractCreated(final Context ctx) {
    return (ctx != null) ? ctx.created : TimeUtil.nowMs();
  }

  private CurrentUser extractCurrentUser(final Context ctx) {
    if (ctx != null) {
      SshSession session = ctx.getSession();
      return (session == null) ? null : session.getCurrentUser();
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
