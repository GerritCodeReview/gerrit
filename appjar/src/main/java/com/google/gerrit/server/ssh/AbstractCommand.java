// Copyright 2008 Google Inc.
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

import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.rpc.Common;
import com.google.gerrit.git.RepositoryCache;
import com.google.gerrit.server.GerritServer;
import com.google.gwtjsonrpc.server.XsrfException;
import com.google.gwtorm.client.OrmException;

import org.apache.sshd.server.CommandFactory.Command;
import org.apache.sshd.server.CommandFactory.ExitCallback;
import org.apache.sshd.server.CommandFactory.SessionAware;
import org.apache.sshd.server.session.ServerSession;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/** Basic command implementation invoked by {@link GerritCommandFactory}. */
abstract class AbstractCommand implements Command, SessionAware {
  protected InputStream in;
  protected OutputStream out;
  protected OutputStream err;
  protected ExitCallback exit;
  protected ServerSession session;
  private String name;
  private String[] args;

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
      throw new Failure(128, "fatal: Gerrit is not available");
    } catch (XsrfException e) {
      throw new Failure(128, "fatal: Gerrit is not available");
    }
  }

  protected RepositoryCache getRepositoryCache() throws Failure {
    final RepositoryCache rc = getGerritServer().getRepositoryCache();
    if (rc == null) {
      throw new Failure(128, "fatal: Gerrit repositories are not available");
    }
    return rc;
  }

  protected ReviewDb openReviewDb() throws Failure {
    try {
      return Common.getSchemaFactory().open();
    } catch (OrmException e) {
      throw new Failure(1, "fatal: Gerrit database is offline");
    }
  }

  protected Account.Id getAccountId() {
    return session.getAttribute(SshUtil.CURRENT_ACCOUNT);
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
        run(args);
      } catch (IOException e) {
        rc = 1;
      } catch (Failure e) {
        rc = e.exitCode;
        try {
          err.write((e.getMessage() + '\n').getBytes("UTF-8"));
          err.flush();
        } catch (IOException err) {
        }
      }
    } finally {
      try {
        in.close();
      } catch (IOException err) {
      }

      try {
        out.close();
      } catch (IOException err) {
      }

      try {
        err.close();
      } catch (IOException err) {
      }

      exit.onExit(rc);
    }
  }

  protected abstract void run(final String args[]) throws IOException, Failure;

  public static class Failure extends Exception {
    final int exitCode;

    public Failure(final int exitCode, final String why) {
      super(why);
      this.exitCode = exitCode;
    }
  }
}
