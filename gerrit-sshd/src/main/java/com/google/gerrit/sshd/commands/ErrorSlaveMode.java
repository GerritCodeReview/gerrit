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

import com.google.gerrit.sshd.BaseCommand;

import org.apache.sshd.server.Environment;

import java.io.IOException;

/**
 * A command which just throws an error because it shouldn't be ran on this
 * server. This is used when a user tries to run a command on a server in Slave
 * Mode, but the command only applies to the Master server.
 */
public final class ErrorSlaveMode extends BaseCommand {
  @Override
  public void start(final Environment env) {
    String msg =
        "error: That command is disabled on this server.\n\n"
            + "Please use the master server URL.\n";
    try {
      err.write(msg.getBytes(ENC));
      err.flush();
    } catch (IOException e) {
      // Ignore errors writing to the client
    }
    onExit(1);
  }
}
