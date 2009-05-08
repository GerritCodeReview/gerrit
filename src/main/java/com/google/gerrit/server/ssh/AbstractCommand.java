// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.server.ssh;

import com.google.gerrit.client.data.ProjectCache;
import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.AccountGroup;
import com.google.gerrit.client.reviewdb.ApprovalCategory;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.rpc.BaseServiceImplementation;
import com.google.gerrit.client.rpc.Common;
import com.google.gerrit.git.RepositoryCache;
import com.google.gerrit.server.GerritServer;
import com.google.gwtjsonrpc.server.XsrfException;
import com.google.gwtorm.client.OrmException;

import org.apache.sshd.server.CommandFactory.Command;
import org.apache.sshd.server.CommandFactory.ExitCallback;
import org.apache.sshd.server.CommandFactory.SessionAware;
import org.apache.sshd.server.session.ServerSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Basic command implementation invoked by {@link GerritCommandFactory}. */
abstract class AbstractCommand implements Command, SessionAware {
  private static final Logger log =
      LoggerFactory.getLogger(AbstractCommand.class);

  protected InputStream in;
  protected OutputStream out;
  protected OutputStream err;
  protected ExitCallback exit;
  protected ServerSession session;
  private String name;
  private String[] args;
  private Set<AccountGroup.Id> userGroups;

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
    this.session = session;
  }

  protected GerritServer getGerritServer() throws Failure {
    try {
      return GerritServer.getInstance();
    } catch (OrmException e) {
      throw new Failure(128, "fatal: Gerrit is not available", e);
    } catch (XsrfException e) {
      throw new Failure(128, "fatal: Gerrit is not available", e);
    }
  }

  protected RepositoryCache getRepositoryCache() throws Failure {
    final RepositoryCache rc = getGerritServer().getRepositoryCache();
    if (rc == null) {
      throw new Failure(128, "fatal: Gerrit repositories are not available",
          new IllegalStateException("git_base_path not set in system_config"));
    }
    return rc;
  }

  protected ReviewDb openReviewDb() throws Failure {
    try {
      return Common.getSchemaFactory().open();
    } catch (OrmException e) {
      throw new Failure(1, "fatal: Gerrit database is offline", e);
    }
  }

  protected Account.Id getAccountId() {
    return session.getAttribute(SshUtil.CURRENT_ACCOUNT);
  }

  protected SocketAddress getRemoteAddress() {
    return session.getAttribute(SshUtil.REMOTE_PEER);
  }

  protected Set<AccountGroup.Id> getGroups() {
    if (userGroups == null) {
      userGroups = Common.getGroupCache().getEffectiveGroups(getAccountId());
    }
    return userGroups;
  }

  protected boolean canRead(final ProjectCache.Entry project) {
    return canPerform(project, ApprovalCategory.READ, (short) 1);
  }

  protected boolean canPerform(final ProjectCache.Entry project,
      final ApprovalCategory.Id actionId, final short val) {
    return BaseServiceImplementation.canPerform(getGroups(), project, actionId,
        val, false);
  }

  protected void assertIsAdministrator() throws Failure {
    if (!Common.getGroupCache().isAdministrator(getAccountId())) {
      throw new Failure(1, "fatal: Not a Gerrit administrator");
    }
  }

  protected String getName() {
    return name;
  }

  void parseArguments(final String cmdName, final String line) {
    final List<String> list = new ArrayList<String>();
    boolean inquote = false;
    StringBuilder r = new StringBuilder();
    for (int ip = 0; ip < line.length();) {
      final char b = line.charAt(ip++);
      switch (b) {
        case '\t':
        case ' ':
          if (inquote)
            r.append(b);
          else if (r.length() > 0) {
            list.add(r.toString());
            r = new StringBuilder();
          }
          continue;
        case '\'':
          inquote = !inquote;
          continue;
        case '\\':
          if (inquote || ip == line.length())
            r.append(b); // literal within a quote
          else
            r.append(line.charAt(ip++));
          continue;
        default:
          r.append(b);
          continue;
      }
    }
    if (r.length() > 0) {
      list.add(r.toString());
    }
    name = cmdName;
    args = list.toArray(new String[list.size()]);
  }

  public void start() {
    final String who = session.getUsername() + "," + getAccountId();
    new Thread("Execute " + getName() + " [" + who + "]") {
      @Override
      public void run() {
        runImp();
      }
    }.start();
  }

  private void runImp() {
    int rc = 0;
    try {
      try {
        try {
          run(args);
        } catch (IOException e) {
          if (e.getClass() == IOException.class
              && "Pipe closed".equals(e.getMessage())) {
            // This is sshd telling us the client just dropped off while
            // we were waiting for a read or a write to complete. Either
            // way its not really a fatal error. Don't log it.
            //
            throw new UnloggedFailure(127, "error: client went away", e);
          }

          throw new Failure(128, "fatal: unexpected IO error", e);

        } catch (RuntimeException e) {
          throw new Failure(128, "fatal: internal server error", e);

        } catch (Error e) {
          throw new Failure(128, "fatal: internal server error", e);

        }
      } catch (Failure e) {
        if (!(e instanceof UnloggedFailure)) {
          final StringBuilder logmsg = beginLogMessage();
          logmsg.append(": ");
          logmsg.append(e.getMessage());
          if (e.getCause() != null)
            log.error(logmsg.toString(), e.getCause());
          else
            log.error(logmsg.toString());
        }

        rc = e.exitCode;
        try {
          err.write((e.getMessage() + '\n').getBytes("UTF-8"));
        } catch (IOException err) {
        }
      }
    } finally {
      try {
        out.flush();
      } catch (IOException err) {
      }

      try {
        err.flush();
      } catch (IOException err) {
      }

      exit.onExit(rc);
    }
  }

  private StringBuilder beginLogMessage() {
    final StringBuilder logmsg = new StringBuilder();
    logmsg.append("sshd error (account ");
    logmsg.append(getAccountId());
    logmsg.append("): ");
    logmsg.append(name);
    for (final String a : args) {
      logmsg.append(' ');
      logmsg.append(a);
    }
    return logmsg;
  }

  protected abstract void run(final String args[]) throws IOException, Failure;

  public static class Failure extends Exception {
    final int exitCode;

    public Failure(final int exitCode, final String msg) {
      this(exitCode, msg, null);
    }

    public Failure(final int exitCode, final String msg, final Throwable why) {
      super(msg, why);
      this.exitCode = exitCode;
    }
  }

  public static class UnloggedFailure extends Failure {
    public UnloggedFailure(final int exitCode, final String msg) {
      this(exitCode, msg, null);
    }

    public UnloggedFailure(final int exitCode, final String msg,
        final Throwable why) {
      super(exitCode, msg, why);
    }
  }
}
