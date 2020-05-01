// Copyright (C) 2020 The Android Open Source Project
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
import com.google.gerrit.extensions.events.AccountDeactivatedListener;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.inject.Inject;
import java.io.IOException;
import org.apache.sshd.common.io.IoAcceptor;
import org.apache.sshd.common.session.helpers.AbstractSession;

/** Closes open SSH connections upon account deactivation. */
public class InactiveAccountDisconnector implements AccountDeactivatedListener {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final SshDaemon sshDaemon;

  @Inject
  InactiveAccountDisconnector(SshDaemon sshDaemon) {
    this.sshDaemon = sshDaemon;
  }

  @Override
  public void onAccountDeactivated(int id) {
    IoAcceptor ioAcceptor = sshDaemon.getIoAcceptor();
    if (ioAcceptor != null) {
      ioAcceptor
          .getManagedSessions()
          .forEach(
              (k, ioSession) -> {
                AbstractSession abstractSession = AbstractSession.getSession(ioSession, true);
                if (abstractSession != null) {
                  SshSession sshSession = abstractSession.getAttribute(SshSession.KEY);
                  if (sshSession != null) {
                    CurrentUser sessionUser = sshSession.getUser();
                    if (sessionUser.isIdentifiedUser()) {
                      IdentifiedUser identifiedUser = sessionUser.asIdentifiedUser();
                      int identifiedUserId = identifiedUser.getAccountId().get();
                      if (identifiedUserId == id) {
                        logger.atInfo().log(
                            "Disconnecting SSH session %s because user %s(%d) got deactivated",
                            abstractSession,
                            identifiedUser.getUserName().orElse(""),
                            identifiedUserId);
                        try {
                          abstractSession.disconnect(-1, "user deactivated");
                        } catch (IOException e) {
                          logger.atWarning().withCause(e).log(
                              "Failure while deactivating session %s", abstractSession);
                        }
                      }
                    }
                  }
                }
              });
    }
  }
}
