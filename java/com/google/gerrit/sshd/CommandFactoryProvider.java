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

import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.Atomics;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.logging.LoggingContextAwareExecutorService;
import com.google.gerrit.sshd.SshScope.Context;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.command.CommandFactory;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.server.session.ServerSessionAware;
import org.eclipse.jgit.lib.Config;

/** Creates a CommandFactory using commands registered by {@link CommandModule}. */
@Singleton
class CommandFactoryProvider implements Provider<CommandFactory>, LifecycleListener {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final DispatchCommandProvider dispatcher;
  private final SshLog log;
  private final SshScope sshScope;
  private final ScheduledExecutorService startExecutor;
  private final ExecutorService destroyExecutor;
  private final DynamicItem<SshCreateCommandInterceptor> createCommandInterceptor;

  @Inject
  CommandFactoryProvider(
      @CommandName(Commands.ROOT) DispatchCommandProvider d,
      @GerritServerConfig Config cfg,
      WorkQueue workQueue,
      SshLog l,
      SshScope s,
      DynamicItem<SshCreateCommandInterceptor> i) {
    dispatcher = d;
    log = l;
    sshScope = s;
    createCommandInterceptor = i;

    int threads = cfg.getInt("sshd", "commandStartThreads", 2);
    startExecutor = workQueue.createQueue(threads, "SshCommandStart", true);
    destroyExecutor =
        new LoggingContextAwareExecutorService(
            Executors.newSingleThreadExecutor(
                new ThreadFactoryBuilder()
                    .setNameFormat("SshCommandDestroy-%s")
                    .setDaemon(true)
                    .build()));
  }

  @Override
  public void start() {}

  @Override
  public void stop() {
    destroyExecutor.shutdownNow();
  }

  @Override
  public CommandFactory get() {
    return (channelSession, requestCommand) -> {
      String command = requestCommand;
      SshCreateCommandInterceptor interceptor = createCommandInterceptor.get();
      if (interceptor != null) {
        command = interceptor.intercept(command);
      }
      return new Trampoline(command);
    };
  }

  private class Trampoline implements Command, ServerSessionAware {
    private final String commandLine;
    private final String[] argv;
    private InputStream in;
    private OutputStream out;
    private OutputStream err;
    private ExitCallback exit;
    private Environment env;
    private Context ctx;
    private DispatchCommand cmd;
    private final AtomicBoolean logged;
    private final AtomicReference<Future<?>> task;

    Trampoline(String cmdLine) {
      commandLine = cmdLine;
      argv = split(cmdLine);
      logged = new AtomicBoolean();
      task = Atomics.newReference();
    }

    @Override
    public void setInputStream(InputStream in) {
      this.in = in;
    }

    @Override
    public void setOutputStream(OutputStream out) {
      this.out = out;
    }

    @Override
    public void setErrorStream(OutputStream err) {
      this.err = err;
    }

    @Override
    public void setExitCallback(ExitCallback callback) {
      this.exit = callback;
    }

    @Override
    public void setSession(ServerSession session) {
      final SshSession s = session.getAttribute(SshSession.KEY);
      this.ctx = sshScope.newContext(s, commandLine);
    }

    @Override
    public void start(ChannelSession channel, Environment env) throws IOException {
      this.env = env;
      final Context ctx = this.ctx;
      task.set(
          startExecutor.submit(
              new Runnable() {
                @Override
                public void run() {
                  try {
                    onStart(channel);
                  } catch (Exception e) {
                    logger.atWarning().withCause(e).log(
                        "Cannot start command \"%s\" for user %s",
                        ctx.getCommandLine(), ctx.getSession().getUsername());
                  }
                }

                @Override
                public String toString() {
                  return "start (user " + ctx.getSession().getUsername() + ")";
                }
              }));
    }

    private void onStart(ChannelSession channel) throws IOException {
      synchronized (this) {
        final Context old = sshScope.set(ctx);
        try {
          cmd = dispatcher.get();
          cmd.setArguments(argv);
          cmd.setInputStream(in);
          cmd.setOutputStream(out);
          cmd.setErrorStream(err);
          cmd.setExitCallback(
              new ExitCallback() {
                @Override
                public void onExit(int rc, String exitMessage, boolean closeImmediately) {
                  exit.onExit(translateExit(rc), exitMessage, closeImmediately);
                  log(rc, exitMessage);
                }

                @Override
                public void onExit(int rc, String exitMessage) {
                  exit.onExit(translateExit(rc), exitMessage);
                  log(rc, exitMessage);
                }

                @Override
                public void onExit(int rc) {
                  exit.onExit(translateExit(rc));
                  log(rc);
                }
              });
          cmd.start(channel, env);
        } finally {
          sshScope.set(old);
        }
      }
    }

    private int translateExit(int rc) {
      switch (rc) {
        case BaseCommand.STATUS_NOT_ADMIN:
          return 1;

        case BaseCommand.STATUS_CANCEL:
          return 15 /* SIGKILL */;

        case BaseCommand.STATUS_NOT_FOUND:
          return 127 /* POSIX not found */;

        default:
          return rc;
      }
    }

    private void log(int rc) {
      if (logged.compareAndSet(false, true)) {
        log.onExecute(cmd, rc, ctx.getSession());
      }
    }

    private void log(int rc, String message) {
      if (logged.compareAndSet(false, true)) {
        log.onExecute(cmd, rc, ctx.getSession(), message);
      }
    }

    @Override
    public void destroy(ChannelSession channel) {
      Future<?> future = task.getAndSet(null);
      if (future != null) {
        future.cancel(true);
        destroyExecutor.execute(() -> onDestroy(channel));
      }
    }

    private void onDestroy(ChannelSession channel) {
      synchronized (this) {
        if (cmd != null) {
          final Context old = sshScope.set(ctx);
          try {
            cmd.destroy(channel);
            log(BaseCommand.STATUS_CANCEL);
          } finally {
            ctx = null;
            cmd = null;
            sshScope.set(old);
          }
        }
      }
    }
  }

  /** Split a command line into a string array. */
  public static String[] split(String commandLine) {
    final List<String> list = new ArrayList<>();
    boolean inquote = false;
    boolean inDblQuote = false;
    StringBuilder r = new StringBuilder();
    for (int ip = 0; ip < commandLine.length(); ) {
      final char b = commandLine.charAt(ip++);
      switch (b) {
        case '\t':
        case ' ':
          if (inquote || inDblQuote) {
            r.append(b);
          } else if (r.length() > 0) {
            list.add(r.toString());
            r = new StringBuilder();
          }
          continue;
        case '\"':
          if (inquote) {
            r.append(b);
          } else {
            inDblQuote = !inDblQuote;
          }
          continue;
        case '\'':
          if (inDblQuote) {
            r.append(b);
          } else {
            inquote = !inquote;
          }
          continue;
        case '\\':
          if (inquote || ip == commandLine.length()) {
            r.append(b); // literal within a quote
          } else {
            r.append(commandLine.charAt(ip++));
          }
          continue;
        default:
          r.append(b);
          continue;
      }
    }
    if (r.length() > 0) {
      list.add(r.toString());
    }
    return list.toArray(new String[list.size()]);
  }
}
