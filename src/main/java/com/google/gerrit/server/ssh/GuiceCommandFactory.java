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

import com.google.gerrit.server.ssh.SshScopes.Context;
import com.google.inject.Provider;

import org.apache.sshd.server.CommandFactory;

import java.io.IOException;
import java.util.Map;

/**
 * Creates a command implementation by looking up an entry in Guice.
 * <p>
 * Commands can be registered in Guice through a {@link CommandModule}, using
 * the {@link CommandModule#command(String)} binding to connect a String command
 * name to a command implementation.
 */
class GuiceCommandFactory implements CommandFactory {
  private final Map<String, Provider<Command>> commands;

  GuiceCommandFactory(final Map<String, Provider<Command>> c) {
    commands = c;
  }

  public Command createCommand(final String commandLine) {
    return new BaseCommand() {
      @Override
      public void start() throws IOException {
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

        final Provider<Command> p = commands.get(cmd);
        if (p != null) {
          final Context old = SshScopes.current.get();
          try {
            SshScopes.current.set(new Context(session));
            final Command c = p.get();
            if (c instanceof AbstractCommand) {
              ((AbstractCommand) c).setCommandLine(cmd, args);
            }
            delegateTo(c);
          } finally {
            SshScopes.current.set(old);
          }
        } else {
          final String msg = "gerrit: " + cmd + ": not found\n";
          err.write(msg.getBytes("UTF-8"));
          err.flush();
          exit.onExit(127);
        }
      }
    };
  }
}
