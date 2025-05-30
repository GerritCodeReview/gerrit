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

import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.PeerDaemonUser;
import com.google.gerrit.server.audit.SshAuditEvent;
import com.google.gerrit.server.config.ConfigKey;
import com.google.gerrit.server.config.ConfigUpdatedEvent;
import com.google.gerrit.server.config.ConfigUpdatedEvent.ConfigUpdateEntry;
import com.google.gerrit.server.config.ConfigUpdatedEvent.UpdateResult;
import com.google.gerrit.server.config.GerritConfigListener;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.LogConfig;
import com.google.gerrit.server.group.GroupAuditService;
import com.google.gerrit.server.ioutil.HexFormat;
import com.google.gerrit.server.util.SystemLog;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gerrit.sshd.SshScope.Context;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import org.apache.log4j.AsyncAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.eclipse.jgit.lib.Config;

@Singleton
class SshLog implements LifecycleListener, GerritConfigListener {
  private static final Logger log = Logger.getLogger(SshLog.class);

  private static final String JSON_SUFFIX = ".json";

  protected static final String LOG_NAME = "sshd_log";
  protected static final String P_SESSION = "session";
  protected static final String P_TRACE_ID = "traceId";
  protected static final String P_USER_NAME = "userName";
  protected static final String P_ACCOUNT_ID = "accountId";
  protected static final String P_WAIT = "queueWaitTime";
  protected static final String P_EXEC = "executionTime";
  protected static final String P_STATUS = "status";
  protected static final String P_AGENT = "agent";
  protected static final String P_MESSAGE = "message";
  protected static final String P_TOTAL_CPU = "totalCpu";
  protected static final String P_USER_CPU = "userCpu";
  protected static final String P_MEMORY = "memory";

  private final Provider<SshSession> session;
  private final Provider<Context> context;
  private volatile AsyncAppender async;
  private final GroupAuditService auditService;
  private final SystemLog systemLog;

  private final boolean json;
  private final boolean text;

  private final Object lock = new Object();

  @Inject
  SshLog(
      final Provider<SshSession> session,
      final Provider<Context> context,
      SystemLog systemLog,
      @GerritServerConfig Config config,
      LogConfig logConfig,
      GroupAuditService auditService) {
    this.session = session;
    this.context = context;
    this.auditService = auditService;
    this.systemLog = systemLog;

    this.json = logConfig.isJsonLogging();
    this.text = logConfig.isTextLogging();

    if (config.getBoolean("sshd", "requestLog", true)) {
      enableLogging();
    }
  }

  /** Returns true if a change in state has occurred */
  public boolean enableLogging() {
    synchronized (lock) {
      if (async == null) {
        async = new AsyncAppender();

        if (text) {
          async.addAppender(systemLog.createAsyncAppender(LOG_NAME, new SshLogLayout()));
        }

        if (json) {
          async.addAppender(
              systemLog.createAsyncAppender(LOG_NAME + JSON_SUFFIX, new SshLogJsonLayout()));
        }
        return true;
      }
      return false;
    }
  }

  /** Returns true if a change in state has occurred */
  public boolean disableLogging() {
    synchronized (lock) {
      if (async != null) {
        async.close();
        async = null;
        return true;
      }
      return false;
    }
  }

  @Override
  public void start() {}

  @Override
  public void stop() {
    disableLogging();
  }

  void onLogin() {
    LoggingEvent entry = log("LOGIN FROM " + session.get().getRemoteAddressAsString());
    if (async != null) {
      async.append(entry);
    }
    audit(context.get(), "0", "LOGIN");
  }

  void onAuthFail(SshSession sd) {
    final LoggingEvent event =
        new LoggingEvent( //
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

  void onExecute(DispatchCommand dcmd, int exitValue, SshSession sshSession) {
    onExecute(dcmd, exitValue, sshSession, null);
  }

  void onExecute(DispatchCommand dcmd, int exitValue, SshSession sshSession, String message) {
    final Context ctx = context.get();
    ctx.finish();

    String cmd = extractWhat(dcmd);

    final LoggingEvent event = log(cmd);
    event.setProperty(P_WAIT, ctx.getWait() + "ms");
    event.setProperty(P_EXEC, ctx.getExec() + "ms");
    event.setProperty(P_TOTAL_CPU, ctx.getTotalCpu() + "ms");
    event.setProperty(P_USER_CPU, ctx.getUserCpu() + "ms");
    event.setProperty(P_MEMORY, String.valueOf(ctx.getAllocatedMemory()));

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
    String peerAgent = sshSession.getPeerAgent();
    if (peerAgent != null) {
      event.setProperty(P_AGENT, peerAgent);
    }

    if (message != null) {
      event.setProperty(P_MESSAGE, message);
    }

    if (async != null) {
      async.append(event);
    }
    audit(context.get(), status, dcmd);
  }

  private ListMultimap<String, ?> extractParameters(DispatchCommand dcmd) {
    if (dcmd == null) {
      return MultimapBuilder.hashKeys(0).arrayListValues(0).build();
    }
    String[] cmdArgs = dcmd.getArguments();
    String paramName = null;
    int argPos = 0;
    ListMultimap<String, String> parms = MultimapBuilder.hashKeys().arrayListValues().build();
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

  private LoggingEvent log(String msg) {
    final SshSession sd = session.get();
    final CurrentUser user = sd.getUser();

    final LoggingEvent event =
        new LoggingEvent( //
            Logger.class.getName(), // fqnOfCategoryClass
            log, // logger
            TimeUtil.nowMs(), // when
            Level.INFO, // level
            msg, // message text
            Thread.currentThread().getName(), // thread name
            null, // exception information
            null, // current NDC string
            null, // caller location
            null // MDC properties
            );

    event.setProperty(P_SESSION, id(sd.getSessionId()));
    String traceId = context.get().getTraceId();
    if (traceId != null) {
      event.setProperty(P_TRACE_ID, context.get().getTraceId());
    }

    String userName = "-";
    String accountId = "-";

    if (user != null && user.isIdentifiedUser()) {
      IdentifiedUser u = user.asIdentifiedUser();
      userName = u.getUserName().orElse(null);
      accountId = "a/" + u.getAccountId().toString();

    } else if (user instanceof PeerDaemonUser) {
      userName = PeerDaemonUser.USER_NAME;
    }

    event.setProperty(P_USER_NAME, userName);
    event.setProperty(P_ACCOUNT_ID, accountId);

    return event;
  }

  private static String id(int id) {
    return HexFormat.fromInt(id);
  }

  void audit(Context ctx, Object result, String cmd) {
    audit(ctx, result, cmd, null);
  }

  void audit(Context ctx, Object result, DispatchCommand cmd) {
    audit(ctx, result, extractWhat(cmd), extractParameters(cmd));
  }

  private void audit(Context ctx, Object result, String cmd, ListMultimap<String, ?> params) {
    String sessionId;
    CurrentUser currentUser;
    long created;
    if (ctx == null) {
      sessionId = null;
      currentUser = null;
      created = TimeUtil.nowMs();
    } else {
      SshSession session = ctx.getSession();
      sessionId = HexFormat.fromInt(session.getSessionId());
      currentUser = session.getUser();
      created = ctx.getCreated();
    }
    auditService.dispatch(new SshAuditEvent(sessionId, currentUser, cmd, created, params, result));
  }

  private String extractWhat(DispatchCommand dcmd) {
    if (dcmd == null) {
      return "Command was already destroyed";
    }
    StringBuilder commandName = new StringBuilder(dcmd.getCommandName());
    String[] args = dcmd.getArguments();
    for (int i = 1; i < args.length; i++) {
      commandName.append(".").append(args[i]);
    }
    return commandName.toString();
  }

  @Override
  public Multimap<UpdateResult, ConfigUpdateEntry> configUpdated(ConfigUpdatedEvent event) {
    ConfigKey sshdRequestLog = ConfigKey.create("sshd", "requestLog");
    if (!event.isValueUpdated(sshdRequestLog)) {
      return ConfigUpdatedEvent.NO_UPDATES;
    }
    boolean stateUpdated;
    try {
      boolean enabled = event.getNewConfig().getBoolean("sshd", "requestLog", true);
      if (enabled) {
        stateUpdated = enableLogging();
      } else {
        stateUpdated = disableLogging();
      }
      return stateUpdated ? event.accept(sshdRequestLog) : ConfigUpdatedEvent.NO_UPDATES;
    } catch (IllegalArgumentException iae) {
      return event.reject(sshdRequestLog);
    }
  }
}
