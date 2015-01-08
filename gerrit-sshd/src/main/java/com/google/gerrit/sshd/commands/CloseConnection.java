// Copyright (C) 2015 The Android Open Source Project
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

import static com.google.gerrit.sshd.CommandMetaData.Mode.MASTER_OR_SLAVE;

import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.sshd.AdminHighPriorityCommand;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.gerrit.sshd.SshDaemon;
import com.google.gerrit.sshd.SshSession;
import com.google.inject.Inject;

import org.apache.sshd.common.io.IoAcceptor;
import org.apache.sshd.common.io.IoSession;
import org.apache.sshd.server.session.ServerSession;
import org.kohsuke.args4j.Argument;

import java.util.ArrayList;
import java.util.List;

/** Close specified SSH connections */
@AdminHighPriorityCommand
@RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
@CommandMetaData(name = "close-connection", description = "Close the specified SSH connection", runsAt = MASTER_OR_SLAVE)
final class CloseConnection extends SshCommand {

  @Inject
  private SshDaemon sshDaemon;

  @Argument(index = 0, multiValued = true, required = true, metaVar = "SESSION_ID")
  private final List<String> sessionIds = new ArrayList<>();

  @Override
  protected void run() throws Failure {

    IoAcceptor acceptor = sshDaemon.getIoAcceptor();
    if (acceptor == null) {
      throw new Failure(1, "fatal: sshd no longer running");
    }
    for (String sessionId : sessionIds) {
      boolean connectionFound = false;
      int id = (int) Long.parseLong(sessionId, 16);
      for (final IoSession io : acceptor.getManagedSessions().values()) {
        ServerSession serverSession =
            (ServerSession) ServerSession.getSession(io, true);
        SshSession sshSession =
            serverSession != null ? serverSession.getAttribute(SshSession.KEY)
                : null;
        if (sshSession.getSessionId() == id) {
          connectionFound = true;
          stdout.println("closing connection " + sessionId + "...");
          io.close(true);
          break;
        }
      }
      if (!connectionFound) {
        stderr.print("close connection " + id + ": no such connection\n");
      }
    }
  }
}
