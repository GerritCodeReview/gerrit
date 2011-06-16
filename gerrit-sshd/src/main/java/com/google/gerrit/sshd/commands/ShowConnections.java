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

package com.google.gerrit.sshd.commands;

import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.util.IdGenerator;
import com.google.gerrit.sshd.BaseCommand;
import com.google.gerrit.sshd.SshDaemon;
import com.google.gerrit.sshd.SshSession;
import com.google.inject.Inject;

import org.apache.mina.core.service.IoAcceptor;
import org.apache.mina.core.session.IoSession;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.session.ServerSession;
import org.kohsuke.args4j.Option;

import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/** Show the current SSH connections. */
final class ShowConnections extends BaseCommand {
  @Option(name = "--numeric", aliases = {"-n"}, usage = "don't resolve names")
  private boolean numeric;

  private PrintWriter p;

  @Inject
  IdentifiedUser currentUser;

  @Inject
  private SshDaemon daemon;

  @Override
  public void start(final Environment env) {
    startThread(new CommandRunnable() {
      @Override
      public void run() throws Exception {
        if (!currentUser.getCapabilities().canViewConnections()) {
          String msg = String.format(
            "fatal: %s does not have \"View Connections\" capability.",
            currentUser.getUserName());
          throw new UnloggedFailure(BaseCommand.STATUS_NOT_ADMIN, msg);
        }

        parseCommandLine();
        ShowConnections.this.display();
      }
    });
  }

  private void display() throws Failure {
    p = toPrintWriter(out);

    final IoAcceptor acceptor = daemon.getIoAcceptor();
    if (acceptor == null) {
      throw new Failure(1, "fatal: sshd no longer running");
    }

    final List<IoSession> list =
        new ArrayList<IoSession>(acceptor.getManagedSessions().values());
    Collections.sort(list, new Comparator<IoSession>() {
      @Override
      public int compare(IoSession arg0, IoSession arg1) {
        if (arg0.getCreationTime() < arg1.getCreationTime()) {
          return -1;
        } else if (arg0.getCreationTime() > arg1.getCreationTime()) {
          return 1;
        }
        return (int) (arg0.getId() - arg1.getId());
      }
    });

    final long now = System.currentTimeMillis();
    p.print(String.format("%-8s %8s %8s   %-15s %s\n", //
        "Session", "Start", "Idle", "User", "Remote Host"));
    p.print("--------------------------------------------------------------\n");
    for (final IoSession io : list) {
      ServerSession s = (ServerSession) ServerSession.getSession(io, true);
      SshSession sd = s != null ? s.getAttribute(SshSession.KEY) : null;

      final SocketAddress remoteAddress = io.getRemoteAddress();
      final long start = io.getCreationTime();
      final long idle = now - io.getLastIoTime();

      p.print(String.format("%8s %8s %8s  %-15.15s %.30s\n", //
          id(sd), //
          time(now, start), //
          age(idle), //
          username(sd), //
          hostname(remoteAddress)));
    }
    p.print("--\n");

    p.flush();
  }

  private static String id(final SshSession sd) {
    return sd != null ? IdGenerator.format(sd.getSessionId()) : "";
  }

  private static String time(final long now, final long time) {
    if (time - now < 24 * 60 * 60 * 1000L) {
      return new SimpleDateFormat("HH:mm:ss").format(new Date(time));
    }
    return new SimpleDateFormat("MMM-dd").format(new Date(time));
  }

  private static String age(long age) {
    age /= 1000;

    final int sec = (int) (age % 60);
    age /= 60;

    final int min = (int) (age % 60);
    age /= 60;

    final int hr = (int) (age % 60);
    return String.format("%02d:%02d:%02d", hr, min, sec);
  }

  private String username(final SshSession sd) {
    if (sd == null) {
      return "";
    }

    final CurrentUser user = sd.getCurrentUser();
    if (user instanceof IdentifiedUser) {
      IdentifiedUser u = (IdentifiedUser) user;

      if (!numeric) {
        String name = u.getAccount().getUserName();
        if (name != null && !name.isEmpty()) {
          return name;
        }
      }

      return "a/" + u.getAccountId().toString();

    } else {
      return "";
    }
  }

  private String hostname(final SocketAddress remoteAddress) {
    if (remoteAddress == null) {
      return "?";
    }
    String host = null;
    if (remoteAddress instanceof InetSocketAddress) {
      final InetSocketAddress sa = (InetSocketAddress) remoteAddress;
      final InetAddress in = sa.getAddress();
      if (numeric) {
        return in.getHostAddress();
      }
      if (in != null) {
        host = in.getCanonicalHostName();
      } else {
        host = sa.getHostName();
      }
    }
    if (host == null) {
      host = remoteAddress.toString();
    }
    return host;
  }
}
