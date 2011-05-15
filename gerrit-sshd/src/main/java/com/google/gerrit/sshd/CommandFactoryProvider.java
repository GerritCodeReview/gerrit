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

import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.sshd.SshScope.Context;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.apache.sshd.server.Command;
import org.apache.sshd.server.CommandFactory;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.SessionAware;
import org.apache.sshd.server.session.ServerSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Creates a CommandFactory using commands registered by {@link CommandModule}.
 */
class CommandFactoryProvider implements Provider<CommandFactory> {
  private static final Logger logger = LoggerFactory
      .getLogger(CommandFactoryProvider.class);

  private final DispatchCommandProvider dispatcher;
  private final SshLog log;
  private final Executor startExecutor;

  @Inject
  CommandFactoryProvider(
      @CommandName(Commands.ROOT) final DispatchCommandProvider d,
      final WorkQueue workQueue, final SshLog l) {
    dispatcher = d;
    log = l;
    startExecutor = workQueue.createQueue(2, "SshCommandStart");
  }

  @Override
  public CommandFactory get() {
    return new CommandFactory() {
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
    private boolean logged;

    Trampoline(final String cmdLine) {
      commandLine = cmdLine;
      argv = split(cmdLine);
    }

    public void setInputStream(final InputStream in) {
      this.in = in;
    }

    public void setOutputStream(final OutputStream out) {
      this.out = out;
    }

    public void setErrorStream(final OutputStream err) {
      this.err = err;
    }

    public void setExitCallback(final ExitCallback callback) {
      this.exit = callback;
    }

    public void setSession(final ServerSession session) {
      final SshSession s = session.getAttribute(SshSession.KEY);
      this.ctx = new Context(s, commandLine);
    }

    public void start(final Environment env) throws IOException {
      this.env = env;
      startExecutor.execute(new Runnable() {
        public void run() {
          try {
            onStart();
          } catch (Exception e) {
            logger.warn("Cannot start command \"" + ctx.getCommandLine()
                + "\" for user " + ctx.getSession().getUsername(), e);
          }
        }

        @Override
        public String toString() {
          return "start (user " + ctx.getSession().getUsername() + ")";
        }
      });
    }

    private void onStart() throws IOException {
      synchronized (this) {
        final Context old = SshScope.set(ctx);
        try {
          cmd = dispatcher.get();
          cmd.setArguments(argv);
          cmd.setInputStream(in);
          cmd.setOutputStream(out);
          cmd.setErrorStream(err);
          cmd.setExitCallback(new ExitCallback() {
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
          SshScope.set(old);
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
      synchronized (this) {
        if (!logged) {
          log.onExecute(rc);
          logged = true;
        }
      }
    }

    @Override
    public void destroy() {
      synchronized (this) {
        if (cmd != null) {
          final Context old = SshScope.set(ctx);
          try {
            cmd.destroy();
            log(BaseCommand.STATUS_CANCEL);
          } finally {
            ctx = null;
            cmd = null;
            SshScope.set(old);
          }
        }
      }
    }
  }

  /** Split a command line into a string array. */
  static String[] split(String commandLine) {
    final List<String> list = new ArrayList<String>();
    boolean inquote = false;
    boolean inDblQuote = false;
    StringBuilder r = new StringBuilder();
    for (int ip = 0; ip < commandLine.length();) {
      final char b = commandLine.charAt(ip++);
      switch (b) {
        case '\t':
        case ' ':
          if (inquote || inDblQuote)
            r.append(b);
          else if (r.length() > 0) {
            list.add(r.toString());
            r = new StringBuilder();
          }
          continue;
        case '\"':
          if (inquote)
            r.append(b);
          else
            inDblQuote = !inDblQuote;
          continue;
        case '\'':
          if (inDblQuote)
            r.append(b);
          else
            inquote = !inquote;
          continue;
        case '\\':
          if (inquote || ip == commandLine.length())
            r.append(b); // literal within a quote
          else
            r.append(commandLine.charAt(ip++));
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
