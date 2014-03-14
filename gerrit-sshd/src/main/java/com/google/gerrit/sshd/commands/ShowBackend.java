
// Copyright (C) 2014 The Android Open Source Project
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
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.gerrit.sshd.SshDaemon;
import com.google.inject.Inject;

import org.apache.sshd.common.io.IoAcceptor;
import org.apache.sshd.common.io.mina.MinaAcceptor;
import org.apache.sshd.common.io.nio2.Nio2Acceptor;

/** Show the current SSH backend. */
@RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
@CommandMetaData(name = "show-backend", description = "Display SSH backend",
  runsAt = MASTER_OR_SLAVE)
public class ShowBackend extends SshCommand {
  @Inject
  private SshDaemon daemon;

  @Override
  protected void run() throws Failure {
    IoAcceptor acceptor = daemon.getIoAcceptor();
    if (acceptor == null) {
      throw new Failure(1, "fatal: sshd no longer running");
    }
    if (acceptor instanceof MinaAcceptor) {
      stdout.print("mina\n");
    } else if (acceptor instanceof Nio2Acceptor) {
      stdout.print("nio2\n");
    } else {
      stdout.print("unknown ssh backend\n");
    }
  }
}
