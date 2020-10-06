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

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.sshd.AdminHighPriorityCommand;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.gerrit.sshd.SshDaemon;
import com.google.gerrit.sshd.SshUtil;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.sshd.common.future.CloseFuture;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

/** Close specified SSH connections */
@AdminHighPriorityCommand
@RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
@CommandMetaData(
    name = "close-connection",
    description = "Close the specified SSH connection",
    runsAt = MASTER_OR_SLAVE)
final class CloseConnection extends SshCommand {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Inject private SshDaemon sshDaemon;

  @Argument(
      index = 0,
      multiValued = true,
      required = true,
      metaVar = "SESSION_ID",
      usage = "List of SSH session IDs to be closed")
  private List<String> sessionIds = new ArrayList<>();

  @Option(name = "--wait", usage = "wait for connection to close before exiting")
  private boolean wait;

  @Override
  protected void run() throws Failure {
    enableGracefulStop();
    SshUtil.forEachSshSession(
        sshDaemon,
        (k, sshSession, abstractSession, ioSession) -> {
          String sessionId = String.format("%08x", sshSession.getSessionId());
          if (sessionIds.remove(sessionId)) {
            stdout.println("closing connection " + sessionId + "...");
            CloseFuture future = ioSession.close(true);
            if (wait) {
              try {
                future.await();
                stdout.println("closed connection " + sessionId);
              } catch (IOException e) {
                logger.atWarning().log(
                    "Wait for connection to close interrupted: %s", e.getMessage());
              }
            }
          }
        });
    for (String sessionId : sessionIds) {
      stderr.print("close connection " + sessionId + ": no such connection\n");
    }
  }
}
