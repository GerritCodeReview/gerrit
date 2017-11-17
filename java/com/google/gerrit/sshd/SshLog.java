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
import com.google.gerrit.server.group.GroupAuditService;
import com.google.gerrit.server.ioutil.HexFormat;
import com.google.gerrit.server.util.SystemLog;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gerrit.sshd.SshScope.Context;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AsyncAppender;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.SimpleMessage;
import org.eclipse.jgit.lib.Config;

@Singleton
class SshLog implements LifecycleListener, GerritConfigListener {
  private static final Logger log = LogManager.getLogger(SshLog.class);

  private static final String JSON_SUFFIX = ".json";

  protected static final String LOG_NAME = "sshd_log";
  protected static final String P_SESSION = "session";
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
      GroupAuditService auditService) {
    this.session = session;
    this.context = context;
    this.auditService = auditService;
    this.systemLog = systemLog;

    this.json = config.getBoolean("log", "jsonLogging", false);
    this.text = config.getBoolean("log", "textLogging", true) || !json;

    if (config.getBoolean("sshd", "requestLog", true)) {
      enableLogging();
    }
  }

  /** Returns true if a change in state has occurred */
  public boolean enableLogging() {
    synchronized (lock) {
      if (async == null) {
        //async = new AsyncAppender();

        Layout<? extends Serializable> layout = new SshLogLayout();

        if (text) {
          async = systemLog.createAsyncAppender(LOG_NAME, layout);
        }

        /*if (json) {
          async.addAppender(
              systemLog.createAsyncAppender(LOG_NAME + JSON_SUFFIX, layout));
        }*/
        return true;
      }
      return false;
    }
  }

  /** Returns true if a change in state has occurred */
  public boolean disableLogging() {
    synchronized (lock) {
      if (async != null) {
        async.stop();
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
    LogEvent entry = log("LOGIN FROM " + session.get().getRemoteAddressAsString());
    if (async != null) {
      async.append(entry);
    }
    audit(context.get(), "0", "LOGIN");
  }

  void onAuthFail(SshSession sd) {
    Map<String, String> map = new HashMap<>();

    map.put(P_SESSION, id(sd.getSessionId()));
    map.put(P_USER_NAME, sd.getUsername());

    final String error = sd.getAuthenticationError();
    if (error != null) {
      map.put(P_STATUS, error);
    }

    final LogEvent event =
        Log4jLogEvent.newBuilder()
            .setLoggerName(log.toString())
            .setLoggerFqcn(Logger.class.getName())
            .setLevel(Level.INFO)
            .setMessage(new SimpleMessage("AUTH FAILURE FROM " + sd.getRemoteAddressAsString()))
            .setThreadName("SSHD")
            .setTimeMillis(TimeUtil.nowMs())
            .setContextMap(map)
            .build();

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

<<<<<<< PATCH SET (e78401 Migrate to log4j2)
    Map<String, String> map = new HashMap<>();
    map.put(P_WAIT, (ctx.started - ctx.created) + "ms");
    map.put(P_EXEC, (ctx.finished - ctx.started) + "ms");
=======
    final LoggingEvent event = log(cmd);
    event.setProperty(P_WAIT, ctx.getWait() + "ms");
    event.setProperty(P_EXEC, ctx.getExec() + "ms");
    event.setProperty(P_TOTAL_CPU, ctx.getTotalCpu() + "ms");
    event.setProperty(P_USER_CPU, ctx.getUserCpu() + "ms");
    event.setProperty(P_MEMORY, String.valueOf(ctx.getAllocatedMemory()));
>>>>>>> BASE      (f8fd64 Merge branch 'stable-3.8')

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
    map.put(P_STATUS, status);
    String peerAgent = sshSession.getPeerAgent();
    if (peerAgent != null) {
      map.put(P_AGENT, peerAgent);
    }

    if (message != null) {
      map.put(P_MESSAGE, message);
    }

    final LogEvent event = log(cmd, map);

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
    LogEvent entry = log("LOGOUT");
    if (async != null) {
      async.append(entry);
    }
    audit(context.get(), "0", "LOGOUT");
  }

  private LogEvent log(String msg) {
    Map<String, String> map = new HashMap<>();
    return log(msg, map);
  }

  private LogEvent log(String msg, Map<String, String> map) {
    final SshSession sd = session.get();
    final CurrentUser user = sd.getUser();

    map.put(P_SESSION, id(sd.getSessionId()));

    String userName = "-";
    String accountId = "-";

    if (user != null && user.isIdentifiedUser()) {
      IdentifiedUser u = user.asIdentifiedUser();
      userName = u.getUserName().orElse(null);
      accountId = "a/" + u.getAccountId().toString();

    } else if (user instanceof PeerDaemonUser) {
      userName = PeerDaemonUser.USER_NAME;
    }

    map.put(P_USER_NAME, userName);
    map.put(P_ACCOUNT_ID, accountId);

    final LogEvent event =
        Log4jLogEvent.newBuilder()
            .setLoggerName(log.toString())
            .setLoggerFqcn(Logger.class.getName())
            .setLevel(Level.INFO)
            .setMessage(new SimpleMessage(msg))
            .setThreadName("SSHD")
            .setTimeMillis(TimeUtil.nowMs())
            .setContextMap(map)
            .build();

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
