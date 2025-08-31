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
import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.eclipse.jgit.lib.Config;

@Singleton
class SshLog implements LifecycleListener, GerritConfigListener {
  private static final Logger log = LogManager.getLogger(SshLog.class);

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
  private final GroupAuditService auditService;
  private final SystemLog systemLog;

  private volatile boolean loggingEnabled;
  private final boolean json;
  private final boolean text;

  private final Object lock = new Object();

  @Inject
  SshLog(
      Provider<SshSession> session,
      Provider<Context> context,
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

  public boolean enableLogging() {
    synchronized (lock) {
      if (!loggingEnabled) {
        if (text) {
          var unused = systemLog.createAsyncAppender(LOG_NAME, new SshLogLayout());
        }
        if (json) {
          var unusedJson = systemLog.createAsyncAppender(LOG_NAME + JSON_SUFFIX, new SshLogJsonLayout());
        }
        loggingEnabled = true;
        return true;
      }
      return false;
    }
  }

  public boolean disableLogging() {
    synchronized (lock) {
      if (loggingEnabled) {
        loggingEnabled = false;
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
    logWithMdc("LOGIN FROM " + session.get().getRemoteAddressAsString(), null);
    audit(context.get(), "0", "LOGIN");
  }

  void onAuthFail(SshSession sd) {
    Map<String, String> mdc = new HashMap<>();
    mdc.put(P_SESSION, HexFormat.fromInt(sd.getSessionId()));
    mdc.put(P_USER_NAME, sd.getUsername());
    String error = sd.getAuthenticationError();
    if (error != null) {
      mdc.put(P_STATUS, error);
    }
    logWithMdc("AUTH FAILURE FROM " + sd.getRemoteAddressAsString(), mdc);
    audit(null, "FAIL", "AUTH");
  }

  void onExecute(DispatchCommand dcmd, int exitValue, SshSession sshSession) {
    onExecute(dcmd, exitValue, sshSession, null);
  }

  void onExecute(
      DispatchCommand dcmd, int exitValue, SshSession sshSession, String message) {
    Context ctx = context.get();
    ctx.finish();

    Map<String, String> mdc = new HashMap<>();
    mdc.put(P_WAIT, ctx.getWait() + "ms");
    mdc.put(P_EXEC, ctx.getExec() + "ms");
    mdc.put(P_TOTAL_CPU, ctx.getTotalCpu() + "ms");
    mdc.put(P_USER_CPU, ctx.getUserCpu() + "ms");
    mdc.put(P_MEMORY, String.valueOf(ctx.getAllocatedMemory()));

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
    mdc.put(P_STATUS, status);

    String peerAgent = sshSession.getPeerAgent();
    if (peerAgent != null) {
      mdc.put(P_AGENT, peerAgent);
    }
    if (message != null) {
      mdc.put(P_MESSAGE, message);
    }

    logWithMdc(extractWhat(dcmd), mdc);
    audit(context.get(), status, dcmd);
  }

  void onLogout() {
    logWithMdc("LOGOUT", null);
    audit(context.get(), "0", "LOGOUT");
  }

  private void logWithMdc(String msg, Map<String, String> mdc) {
    if (mdc != null) {
      mdc.forEach(ThreadContext::put);
    }

    SshSession sd = session.get();
    CurrentUser user = sd.getUser();

    ThreadContext.put(P_SESSION, HexFormat.fromInt(sd.getSessionId()));
    String traceId = context.get().getTraceId();
    if (traceId != null) {
      ThreadContext.put(P_TRACE_ID, traceId);
    }

    String userName = "-";
    String accountId = "-";
    if (user != null && user.isIdentifiedUser()) {
      IdentifiedUser u = user.asIdentifiedUser();
      userName = u.getUserName().orElse("-");
      accountId = "a/" + u.getAccountId();
    } else if (user instanceof PeerDaemonUser) {
      userName = PeerDaemonUser.USER_NAME;
    }

    ThreadContext.put(P_USER_NAME, userName);
    ThreadContext.put(P_ACCOUNT_ID, accountId);

    log.info(msg);

    ThreadContext.clearMap();
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
      if ("--".equals(arg)) {
        for (i++; i < cmdArgs.length; i++) {
          parms.put("$" + argPos++, cmdArgs[i]);
        }
        break;
      }
      int eqPos = arg.indexOf('=');
      if (arg.startsWith("--") && eqPos > 0) {
        parms.put(arg.substring(0, eqPos), arg.substring(eqPos + 1));
        continue;
      }
      if (arg.startsWith("-")) {
        if (paramName != null) {
          parms.put(paramName, null);
        }
        paramName = arg;
        continue;
      }
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

  void audit(Context ctx, Object result, String cmd) {
    audit(ctx, result, cmd, null);
  }

  void audit(Context ctx, Object result, DispatchCommand cmd) {
    audit(ctx, result, extractWhat(cmd), extractParameters(cmd));
  }

  private void audit(Context ctx, Object result, String cmd, ListMultimap<String, ?> params) {
    String sessionId = null;
    CurrentUser currentUser = null;
    long created = TimeUtil.nowMs();
    if (ctx != null) {
      SshSession s = ctx.getSession();
      sessionId = HexFormat.fromInt(s.getSessionId());
      currentUser = s.getUser();
      created = ctx.getCreated();
    }
    auditService.dispatch(new SshAuditEvent(sessionId, currentUser, cmd, created, params, result));
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
