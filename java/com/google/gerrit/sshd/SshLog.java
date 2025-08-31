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
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AsyncAppender;
import org.apache.logging.log4j.core.config.AppenderRef;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.impl.ContextDataFactory;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.SimpleMessage;
import org.apache.logging.log4j.util.SortedArrayStringMap;
import org.apache.logging.log4j.util.StringMap;
import org.eclipse.jgit.lib.Config;

@Singleton
class SshLog implements LifecycleListener, GerritConfigListener {
  private static final String JSON_SUFFIX = ".json";
  private static final String LOG_NAME = "sshd_log";

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
  private boolean reconfiguring = false;

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

      // This is triggered when reconfiguring e.g. in set-level running reset
      LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
      ctx.addPropertyChangeListener(
          evt -> {
            if ("config".equals(evt.getPropertyName())) {
              synchronized (lock) {
                enableLogging();
              }
            }
          });
    }
  }

  /** Returns true if a change in state has occurred */
  public boolean enableLogging() {
    synchronized (lock) {
      if (reconfiguring) return false;
      reconfiguring = true;
      try {
        if (async == null || !async.isStarted()) {
          LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
          Configuration cfg = ctx.getConfiguration();

          if (async != null) {
            async.stop();
            async = null;
          }

          List<AppenderRef> refList = new ArrayList<>();
          if (text) {
            String name = LOG_NAME;
            if (!cfg.getAppenders().containsKey(name)) {
              cfg.addAppender(systemLog.createAsyncAppender(name, new SshLogLayout()));
            }
            refList.add(AppenderRef.createAppenderRef(name, null, null));
          }

          if (json) {
            String name = LOG_NAME + JSON_SUFFIX;
            if (!cfg.getAppenders().containsKey(name)) {
              cfg.addAppender(systemLog.createAsyncAppender(name, new SshLogJsonLayout()));
            }
            refList.add(AppenderRef.createAppenderRef(name, null, null));
          }

          AppenderRef[] refs = refList.toArray(new AppenderRef[0]);
          async =
              AsyncAppender.newBuilder()
                  .setName("SshAsync")
                  .setAppenderRefs(refs)
                  .setConfiguration(cfg)
                  .build();

          async.start();
          cfg.addAppender(async);
          ctx.updateLoggers();

          return true;
        }
        return false;
      } finally {
        reconfiguring = false;
      }
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
    if (async != null) async.append(entry);
    audit(context.get(), "0", "LOGIN");
  }

  void onAuthFail(SshSession sd) {
    LogEvent base = log("AUTH FAILURE FROM " + sd.getRemoteAddressAsString());
    StringMap ctxData = new SortedArrayStringMap();
    base.getContextData().forEach((k, v) -> ctxData.putValue(k, v));
    if (sd.getAuthenticationError() != null)
      ctxData.putValue(P_STATUS, sd.getAuthenticationError());

    LogEvent event =
        Log4jLogEvent.newBuilder()
            .setLoggerName(base.getLoggerName())
            .setLevel(Level.INFO)
            .setTimeMillis(base.getTimeMillis())
            .setThreadName("SSHD")
            .setMessage(new SimpleMessage(base.getMessage().getFormattedMessage()))
            .setContextData(ctxData)
            .build();

    if (async != null) async.append(event);
    audit(null, "FAIL", "AUTH");
  }

  void onExecute(DispatchCommand dcmd, int exitValue, SshSession sshSession) {
    onExecute(dcmd, exitValue, sshSession, null);
  }

  void onExecute(DispatchCommand dcmd, int exitValue, SshSession sshSession, String message) {
    final Context ctx = context.get();
    ctx.finish();

    LogEvent base = log(extractWhat(dcmd));
    StringMap ctxData = new SortedArrayStringMap();
    base.getContextData().forEach((k, v) -> ctxData.putValue(k, v));

    ctxData.putValue(P_WAIT, ctx.getWait() + "ms");
    ctxData.putValue(P_EXEC, ctx.getExec() + "ms");
    ctxData.putValue(P_TOTAL_CPU, ctx.getTotalCpu() + "ms");
    ctxData.putValue(P_USER_CPU, ctx.getUserCpu() + "ms");
    ctxData.putValue(P_MEMORY, String.valueOf(ctx.getAllocatedMemory()));
    ctxData.putValue(P_STATUS, statusString(exitValue));
    if (sshSession.getPeerAgent() != null) ctxData.putValue(P_AGENT, sshSession.getPeerAgent());
    if (message != null) ctxData.putValue(P_MESSAGE, message);

    LogEvent event =
        Log4jLogEvent.newBuilder()
            .setLoggerName(base.getLoggerName())
            .setLevel(Level.INFO)
            .setTimeMillis(base.getTimeMillis())
            .setThreadName(Thread.currentThread().getName())
            .setMessage(new SimpleMessage(base.getMessage().getFormattedMessage()))
            .setContextData(ctxData)
            .build();

    if (async != null) async.append(event);
    audit(ctx, statusString(exitValue), dcmd);
  }

  void onLogout() {
    LogEvent entry = log("LOGOUT");
    if (async != null) async.append(entry);
    audit(context.get(), "0", "LOGOUT");
  }

  private LogEvent log(String msg) {
    SshSession sd = session.get();
    CurrentUser user = sd.getUser();
    StringMap contextData = ContextDataFactory.createContextData();

    contextData.putValue(P_SESSION, HexFormat.fromInt(sd.getSessionId()));
    String traceId = context.get().getTraceId();
    if (traceId != null) contextData.putValue(P_TRACE_ID, traceId);

    String userName = "-", accountId = "-";
    if (user != null && user.isIdentifiedUser()) {
      IdentifiedUser u = user.asIdentifiedUser();
      userName = u.getUserName().orElse("-");
      accountId = "a/" + u.getAccountId().toString();
    } else if (user instanceof PeerDaemonUser) {
      userName = PeerDaemonUser.USER_NAME;
    }

    contextData.putValue(P_USER_NAME, userName);
    contextData.putValue(P_ACCOUNT_ID, accountId);

    return Log4jLogEvent.newBuilder()
        .setLoggerName(SshLog.class.getName())
        .setLevel(Level.INFO)
        .setTimeMillis(TimeUtil.nowMs())
        .setThreadName(Thread.currentThread().getName())
        .setMessage(new SimpleMessage(msg))
        .setContextData(contextData)
        .build();
  }

  private static String statusString(int exitValue) {
    return switch (exitValue) {
      case BaseCommand.STATUS_CANCEL -> "killed";
      case BaseCommand.STATUS_NOT_FOUND -> "not-found";
      case BaseCommand.STATUS_NOT_ADMIN -> "not-admin";
      default -> String.valueOf(exitValue);
    };
  }

  private ListMultimap<String, ?> extractParameters(DispatchCommand dcmd) {
    if (dcmd == null) return MultimapBuilder.hashKeys(0).arrayListValues(0).build();
    String[] args = dcmd.getArguments();
    String paramName = null;
    int argPos = 0;
    ListMultimap<String, String> parms = MultimapBuilder.hashKeys().arrayListValues().build();
    for (int i = 2; i < args.length; i++) {
      String arg = args[i];
      if (arg.equals("--")) {
        for (i++; i < args.length; i++) parms.put("$" + argPos++, args[i]);
        break;
      }
      int eqPos = arg.indexOf('=');
      if (arg.startsWith("--") && eqPos > 0) {
        parms.put(arg.substring(0, eqPos), arg.substring(eqPos + 1));
        continue;
      }
      if (arg.startsWith("-")) {
        if (paramName != null) parms.put(paramName, null);
        paramName = arg;
        continue;
      }
      if (paramName == null) parms.put("$" + argPos++, arg);
      else {
        parms.put(paramName, arg);
        paramName = null;
      }
    }
    if (paramName != null) parms.put(paramName, null);
    return parms;
  }

  private String extractWhat(DispatchCommand dcmd) {
    if (dcmd == null) return "Command was already destroyed";
    StringBuilder sb = new StringBuilder(dcmd.getCommandName());
    for (int i = 1; i < dcmd.getArguments().length; i++) {
      sb.append(".").append(dcmd.getArguments()[i]);
    }
    return sb.toString();
  }

  void audit(Context ctx, Object result, String cmd) {
    audit(ctx, result, cmd, null);
  }

  void audit(Context ctx, Object result, DispatchCommand cmd) {
    audit(ctx, result, extractWhat(cmd), extractParameters(cmd));
  }

  private void audit(Context ctx, Object result, String cmd, ListMultimap<String, ?> params) {
    String sessionId = null;
    CurrentUser user = null;
    long created = TimeUtil.nowMs();

    if (ctx != null) {
      SshSession ssh = ctx.getSession();
      sessionId = HexFormat.fromInt(ssh.getSessionId());
      user = ssh.getUser();
      created = ctx.getCreated();
    }
    auditService.dispatch(new SshAuditEvent(sessionId, user, cmd, created, params, result));
  }

  @Override
  public Multimap<UpdateResult, ConfigUpdateEntry> configUpdated(ConfigUpdatedEvent event) {
    ConfigKey sshdRequestLog = ConfigKey.create("sshd", "requestLog");
    if (!event.isValueUpdated(sshdRequestLog)) return ConfigUpdatedEvent.NO_UPDATES;
    boolean stateUpdated;
    try {
      boolean enabled = event.getNewConfig().getBoolean("sshd", "requestLog", true);
      stateUpdated = enabled ? enableLogging() : disableLogging();
      return stateUpdated ? event.accept(sshdRequestLog) : ConfigUpdatedEvent.NO_UPDATES;
    } catch (IllegalArgumentException iae) {
      return event.reject(sshdRequestLog);
    }
  }
}
