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

import com.google.inject.Provider;

import org.apache.sshd.server.CommandFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

/** Creates a command implementation based on the client input. */
class GerritCommandFactory implements CommandFactory {
  private final Map<String, Provider<Command>> commands;

  GerritCommandFactory(final Map<String, Provider<Command>> c) {
    commands = c;
  }

  public Command createCommand(final String commandLine) {
    final int sp1 = commandLine.indexOf(' ');
    String cmd, args;
    if (0 < sp1) {
      cmd = commandLine.substring(0, sp1);
      args = commandLine.substring(sp1 + 1);
    } else {
      cmd = commandLine;
      args = "";
    }

    // Support newer-style "git receive-pack" requests by converting
    // to the older-style "git-receive-pack".
    //
    if ("git".equals(cmd) || "gerrit".equals(cmd)) {
      cmd += "-";
      final int sp2 = args.indexOf(' ');
      if (0 < sp2) {
        cmd += args.substring(0, sp2);
        args = args.substring(sp2 + 1);
      } else {
        cmd += args;
        args = "";
      }
    }

    final Command c = create(cmd);
    if (c instanceof AbstractCommand) {
      ((AbstractCommand) c).setCommandLine(cmd, args);
    }
    return c;
  }

  private Command create(final String cmd) {
    final Provider<Command> p = commands.get(cmd);
    if (p != null) {
      return p.get();
    }

    return new Command() {
      private OutputStream err;
      private ExitCallback exit;

      public void setErrorStream(final OutputStream err) {
        this.err = err;
      }

      public void setExitCallback(final ExitCallback callback) {
        this.exit = callback;
      }

      @Override
      public void start() throws IOException {
        final String msg = "gerrit: " + cmd + ": not found\n";
        err.write(msg.getBytes("UTF-8"));
        err.flush();
        exit.onExit(127);
      }

      public void setInputStream(final InputStream in) {
      }

      public void setOutputStream(final OutputStream out) {
      }
    };
  }
}
