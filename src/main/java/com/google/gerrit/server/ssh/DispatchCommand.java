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

package com.google.gerrit.server.ssh;

import com.google.gerrit.server.ssh.SshScopes.Context;
import com.google.inject.Provider;

import org.apache.sshd.server.CommandFactory.Command;

import java.io.IOException;
import java.util.Map;

/**
 * Command that dispatches to a subcommand from its command table.
 */
public class DispatchCommand extends BaseCommand {
  private final String prefix;
  private final Map<String, Provider<Command>> commands;

  public DispatchCommand(final String pfx,
      final Map<String, Provider<Command>> all) {
    prefix = pfx;
    commands = all;
  }

  @Override
  public void start() throws IOException {
    int sp = commandLine.indexOf(' ');
    final String name, args;
    if (0 < sp) {
      name = commandLine.substring(0, sp);
      while (Character.isWhitespace(commandLine.charAt(sp))) {
        sp++;
      }
      args = commandLine.substring(sp);
    } else {
      name = commandLine;
      args = "";
    }

    final Provider<Command> p = commands.get(name);
    if (p != null) {
      final Context old = SshScopes.current.get();
      try {
        if (old == null) {
          SshScopes.current.set(new Context(session));
        }
        final Command cmd = p.get();
        provideStateTo(cmd);
        if (cmd instanceof BaseCommand) {
          final BaseCommand bc = (BaseCommand) cmd;
          if (commandPrefix.isEmpty())
            bc.setCommandPrefix(name);
          else
            bc.setCommandPrefix(commandPrefix + " " + name);
          bc.setCommandLine(args);
        }
        cmd.start();
      } finally {
        SshScopes.current.set(old);
      }
    } else {
      final String msg = prefix + ": " + name + ": not found\n";
      err.write(msg.getBytes("UTF-8"));
      err.flush();
      exit.onExit(127);
    }
  }
}
