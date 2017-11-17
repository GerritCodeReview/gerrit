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
import com.google.common.collect.MultimapBuilder;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.PeerDaemonUser;
import com.google.gerrit.server.audit.AuditService;
import com.google.gerrit.server.audit.SshAuditEvent;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.util.IdGenerator;
import com.google.gerrit.server.util.SystemLog;
import com.google.gerrit.sshd.SshScope.Context;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
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
class SshLog implements LifecycleListener {
  private static final Logger log = LogManager.getLogger(SshLog.class);
  private static final String LOG_NAME = "sshd_log";
  private static final String P_SESSION = "session";
  private static final String P_USER_NAME = "userName";
  private static final String P_ACCOUNT_ID = "accountId";
  private static final String P_WAIT = "queueWaitTime";
  private static final String P_EXEC = "executionTime";
  private static final String P_STATUS = "status";
  private static final String P_AGENT = "agent";

  private final Provider<SshSession> session;
  private final Provider<Context> context;
  private final AsyncAppender async;
  private final AuditService auditService;

  @Inject
  SshLog(
      final Provider<SshSession> session,
      final Provider<Context> context,
      SystemLog systemLog,
      @GerritServerConfig Config config,
      AuditService auditService) {
    this.session = session;
    this.context = context;
    this.auditService = auditService;

    if (!config.getBoolean("sshd", "requestLog", true)) {
      async = null;
      return;
    }
    Layout<? extends Serializable> layout = new SshLogLayout(StandardCharsets.UTF_8);
    async = systemLog.createAsyncAppender(LOG_NAME, layout);
  }

  @Override
  public void start() {}

  @Override
  public void stop() {
    if (async != null) {
      async.stop();
    }
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
    final Context ctx = context.get();
    ctx.finished = TimeUtil.nowMs();

    String cmd = extractWhat(dcmd);

    Map<String, String> map = new HashMap<>();

    map.put(P_WAIT, (ctx.started - ctx.created) + "ms");
    map.put(P_EXEC, (ctx.finished - ctx.started) + "ms");

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
      userName = u.getAccount().getUserName();
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
    return IdGenerator.format(id);
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
      sessionId = IdGenerator.format(session.getSessionId());
      currentUser = session.getUser();
      created = ctx.created;
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
}
