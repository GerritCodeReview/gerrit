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

package com.google.gerrit.server.ssh.commands;

import com.google.gerrit.server.ssh.BaseCommand;

/**
 * A command which just throws an error because it shouldn't be ran on this
 * server. This is used when a user tries to run a command on a server in Slave
 * Mode, but the command only applies to the Master server.
 */
final class ErrorSlaveMode extends BaseCommand {

  @Override
  public void start() {
    startThread(new CommandRunnable() {
      @Override
      public void run() throws Exception {
        throw new UnloggedFailure(1,
            "error: That command is disabled on this server.\n\n"
                + "Please use the master server URL.");
      }
    });
  }
}
