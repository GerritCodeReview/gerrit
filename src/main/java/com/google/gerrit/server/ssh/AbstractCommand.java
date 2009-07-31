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
import com.google.gerrit.client.rpc.Common;
import com.google.gerrit.pgm.CmdLineParser;
import com.google.gerrit.server.BaseServiceImplementation;
import com.google.gerrit.server.GerritServer;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.RemotePeer;
import com.google.gerrit.server.ssh.SshScopes.Context;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.Inject;

import org.apache.sshd.common.SshException;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Basic command implementation invoked by {@link GuiceCommandFactory}. */
public abstract class AbstractCommand extends BaseCommand {
  private static final String ENC = "UTF-8";

  private static final Logger log =
      LoggerFactory.getLogger(AbstractCommand.class);

  @Inject
  protected GerritServer server;

  @Inject
  protected SchemaFactory<ReviewDb> schema;

  @Inject
  @RemotePeer
  private SocketAddress remoteAddress;

  @Inject
  private IdentifiedUser currentUser;

  protected ReviewDb db;

  private String name;
  private String unsplitArguments;
  private Set<AccountGroup.Id> userGroups;

  @Option(name = "--help", usage = "display this help text", aliases = {"-h"})
  private boolean help;

  protected PrintWriter toPrintWriter(final OutputStream o)
      throws UnsupportedEncodingException {
    return new PrintWriter(new BufferedWriter(new OutputStreamWriter(o, ENC)));
  }

  protected GerritServer getGerritServer() {
    return server;
  }

  protected ReviewDb openReviewDb() throws Failure {
    if (db == null) {
      try {
        db = schema.open();
      } catch (OrmException e) {
        throw new Failure(1, "fatal: Gerrit database is offline", e);
      }
    }
    return db;
  }

  protected Account.Id getAccountId() {
    return currentUser.getAccountId();
  }

  protected SocketAddress getRemoteAddress() {
    return remoteAddress;
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
        val);
  }

  protected void assertIsAdministrator() throws Failure {
    if (!Common.getGroupCache().isAdministrator(getAccountId())) {
      throw new Failure(1, "fatal: Not a Gerrit administrator");
    }
  }

  protected String getName() {
    return name;
  }

  public String getCommandLine() {
    return unsplitArguments.length() > 0 ? name + " " + unsplitArguments : name;
  }

  public void setCommandLine(final String cmdName, final String line) {
    name = cmdName;
    unsplitArguments = line;
  }

  private void parseArguments() throws Failure {
    final List<String> list = new ArrayList<String>();
    boolean inquote = false;
    StringBuilder r = new StringBuilder();
    for (int ip = 0; ip < unsplitArguments.length();) {
      final char b = unsplitArguments.charAt(ip++);
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
          if (inquote || ip == unsplitArguments.length())
            r.append(b); // literal within a quote
          else
            r.append(unsplitArguments.charAt(ip++));
          continue;
        default:
          r.append(b);
          continue;
      }
    }
    if (r.length() > 0) {
      list.add(r.toString());
    }

    final CmdLineParser clp = new CmdLineParser(this);
    try {
      clp.parseArgument(list.toArray(new String[list.size()]));
    } catch (CmdLineException err) {
      if (!help) {
        throw new UnloggedFailure(1, "fatal: " + err.getMessage());
      }
    }

    if (help) {
      final StringWriter msg = new StringWriter();
      msg.write(getName());
      clp.printSingleLineUsage(msg, null);
      msg.write('\n');

      msg.write('\n');
      clp.printUsage(msg, null);
      msg.write('\n');
      throw new UnloggedFailure(1, msg.toString());
    }
  }

  public void start() {
    final List<AbstractCommand> list = session.getAttribute(SshUtil.ACTIVE);
    final String who = session.getUsername() + "," + getAccountId();
    final AbstractCommand cmd = this;
    final Context ctx = SshScopes.getContext();
    new Thread("Execute " + getName() + " [" + who + "]") {
      @Override
      public void run() {
        SshScopes.current.set(ctx);
        try {
          synchronized (list) {
            list.add(cmd);
          }
          runImp();
        } finally {
          synchronized (list) {
            list.remove(cmd);
          }
        }
      }
    }.start();
  }

  private void runImp() {
    int rc = 0;
    try {
      try {
        try {
          preRun();
          try {
            parseArguments();
            run();
          } finally {
            postRun();
          }
        } catch (IOException e) {
          if (e.getClass() == IOException.class
              && "Pipe closed".equals(e.getMessage())) {
            // This is sshd telling us the client just dropped off while
            // we were waiting for a read or a write to complete. Either
            // way its not really a fatal error. Don't log it.
            //
            throw new UnloggedFailure(127, "error: client went away", e);
          }

          if (e.getClass() == SshException.class
              && "Already closed".equals(e.getMessage())) {
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
          err.write((e.getMessage() + '\n').getBytes(ENC));
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
    logmsg.append(' ');
    logmsg.append(unsplitArguments);
    return logmsg;
  }

  @SuppressWarnings("unused")
  protected void preRun() throws Failure {
  }

  protected abstract void run() throws IOException, Failure;

  protected void postRun() {
    closeDb();
  }

  protected void closeDb() {
    if (db != null) {
      db.close();
      db = null;
    }
  }

  public static class Failure extends Exception {
    private static final long serialVersionUID = 1L;

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
    private static final long serialVersionUID = 1L;

    public UnloggedFailure(final int exitCode, final String msg) {
      this(exitCode, msg, null);
    }

    public UnloggedFailure(final int exitCode, final String msg,
        final Throwable why) {
      super(exitCode, msg, why);
    }
  }
}
