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

import com.google.common.util.concurrent.Atomics;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.sshd.SshScope.Context;
import com.google.gwtorm.server.SchemaFactory;
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
import org.apache.sshd.server.Command;
import org.apache.sshd.server.CommandFactory;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.SessionAware;
import org.apache.sshd.server.session.ServerSession;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Creates a CommandFactory using commands registered by {@link CommandModule}. */
@Singleton
class CommandFactoryProvider implements Provider<CommandFactory>, LifecycleListener {
  private static final Logger logger = LoggerFactory.getLogger(CommandFactoryProvider.class);

  private final DispatchCommandProvider dispatcher;
  private final SshLog log;
  private final SshScope sshScope;
  private final ScheduledExecutorService startExecutor;
  private final ExecutorService destroyExecutor;
  private final SchemaFactory<ReviewDb> schemaFactory;

  @Inject
  CommandFactoryProvider(
      @CommandName(Commands.ROOT) final DispatchCommandProvider d,
      @GerritServerConfig final Config cfg,
      final WorkQueue workQueue,
      final SshLog l,
      final SshScope s,
      SchemaFactory<ReviewDb> sf) {
    dispatcher = d;
    log = l;
    sshScope = s;
    schemaFactory = sf;

    int threads = cfg.getInt("sshd", "commandStartThreads", 2);
    startExecutor = workQueue.createQueue(threads, "SshCommandStart");
    destroyExecutor =
        Executors.newSingleThreadExecutor(
            new ThreadFactoryBuilder()
                .setNameFormat("SshCommandDestroy-%s")
                .setDaemon(true)
                .build());
  }

  @Override
  public void start() {}

  @Override
  public void stop() {
    destroyExecutor.shutdownNow();
  }

  @Override
  public CommandFactory get() {
    return new CommandFactory() {
      @Override
      public Command createCommand(final String requestCommand) {
        return new Trampoline(requestCommand);
      }
    };
  }

  private class Trampoline implements Command, SessionAware {
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

    Trampoline(final String cmdLine) {
      commandLine = cmdLine;
      argv = split(cmdLine);
      logged = new AtomicBoolean();
      task = Atomics.newReference();
    }

    @Override
    public void setInputStream(final InputStream in) {
      this.in = in;
    }

    @Override
    public void setOutputStream(final OutputStream out) {
      this.out = out;
    }

    @Override
    public void setErrorStream(final OutputStream err) {
      this.err = err;
    }

    @Override
    public void setExitCallback(final ExitCallback callback) {
      this.exit = callback;
    }

    @Override
    public void setSession(final ServerSession session) {
      final SshSession s = session.getAttribute(SshSession.KEY);
      this.ctx = sshScope.newContext(schemaFactory, s, commandLine);
    }

    @Override
    public void start(final Environment env) throws IOException {
      this.env = env;
      final Context ctx = this.ctx;
      task.set(
          startExecutor.submit(
              new Runnable() {
                @Override
                public void run() {
                  try {
                    onStart();
                  } catch (Exception e) {
                    logger.warn(
                        "Cannot start command \""
                            + ctx.getCommandLine()
                            + "\" for user "
                            + ctx.getSession().getUsername(),
                        e);
                  }
                }

                @Override
                public String toString() {
                  return "start (user " + ctx.getSession().getUsername() + ")";
                }
              }));
    }

    private void onStart() throws IOException {
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
                public void onExit(int rc, String exitMessage) {
                  exit.onExit(translateExit(rc), exitMessage);
                  log(rc);
                }

                @Override
                public void onExit(int rc) {
                  exit.onExit(translateExit(rc));
                  log(rc);
                }
              });
          cmd.start(env);
        } finally {
          sshScope.set(old);
        }
      }
    }

    private int translateExit(final int rc) {
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

    private void log(final int rc) {
      if (logged.compareAndSet(false, true)) {
        log.onExecute(cmd, rc, ctx.getSession());
      }
    }

    @Override
    public void destroy() {
      Future<?> future = task.getAndSet(null);
      if (future != null) {
        future.cancel(true);
        destroyExecutor.execute(
            new Runnable() {
              @Override
              public void run() {
                onDestroy();
              }
            });
      }
    }

    private void onDestroy() {
      synchronized (this) {
        if (cmd != null) {
          final Context old = sshScope.set(ctx);
          try {
            cmd.destroy();
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
