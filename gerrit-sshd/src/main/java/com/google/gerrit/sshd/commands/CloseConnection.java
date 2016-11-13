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
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.sshd.common.future.CloseFuture;
import org.apache.sshd.common.io.IoAcceptor;
import org.apache.sshd.common.io.IoSession;
import org.apache.sshd.common.session.helpers.AbstractSession;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Close specified SSH connections */
@AdminHighPriorityCommand
@RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
@CommandMetaData(
  name = "close-connection",
  description = "Close the specified SSH connection",
  runsAt = MASTER_OR_SLAVE
)
final class CloseConnection extends SshCommand {

  private static final Logger log = LoggerFactory.getLogger(CloseConnection.class);

  @Inject private SshDaemon sshDaemon;

  @Argument(
    index = 0,
    multiValued = true,
    required = true,
    metaVar = "SESSION_ID",
    usage = "List of SSH session IDs to be closed"
  )
  private final List<String> sessionIds = new ArrayList<>();

  @Option(name = "--wait", usage = "wait for connection to close before exiting")
  private boolean wait;

  @Override
  protected void run() throws Failure {
    IoAcceptor acceptor = sshDaemon.getIoAcceptor();
    if (acceptor == null) {
      throw new Failure(1, "fatal: sshd no longer running");
    }
    for (String sessionId : sessionIds) {
      boolean connectionFound = false;
      int id = (int) Long.parseLong(sessionId, 16);
      for (IoSession io : acceptor.getManagedSessions().values()) {
        AbstractSession serverSession = AbstractSession.getSession(io, true);
        SshSession sshSession =
            serverSession != null ? serverSession.getAttribute(SshSession.KEY) : null;
        if (sshSession != null && sshSession.getSessionId() == id) {
          connectionFound = true;
          stdout.println("closing connection " + sessionId + "...");
          CloseFuture future = io.close(true);
          if (wait) {
            try {
              future.await();
              stdout.println("closed connection " + sessionId);
            } catch (IOException e) {
              log.warn("Wait for connection to close interrupted: " + e.getMessage());
            }
          }
          break;
        }
      }
      if (!connectionFound) {
        stderr.print("close connection " + sessionId + ": no such connection\n");
      }
    }
  }
}
