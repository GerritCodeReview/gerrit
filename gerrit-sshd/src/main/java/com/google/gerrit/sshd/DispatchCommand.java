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

package com.google.gerrit.sshd;

import com.google.gerrit.server.CurrentUser;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;

import org.apache.sshd.server.Command;
import org.apache.sshd.server.Environment;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Map;

/**
 * Command that dispatches to a subcommand from its command table.
 */
final class DispatchCommand extends BaseCommand {
  interface Factory {
    DispatchCommand create(String prefix, Map<String, Provider<Command>> map);
  }

  private final Provider<CurrentUser> currentUser;
  private final String prefix;
  private final Map<String, Provider<Command>> commands;

  @Inject
  DispatchCommand(final Provider<CurrentUser> cu, @Assisted final String pfx,
      @Assisted final Map<String, Provider<Command>> all) {
    currentUser = cu;
    prefix = pfx;
    commands = all;
  }

  @Override
  public void start(final Environment env) throws IOException {
    if (commandLine.isEmpty()) {
      usage();
      return;
    }

    final String name, args;
    int sp = commandLine.indexOf(' ');
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

    if (name.equals("help") || name.equals("--help") || name.equals("-h")) {
      usage();
      return;
    }

    final Provider<Command> p = commands.get(name);
    if (p != null) {
      final Command cmd = p.get();
      if (cmd.getClass().getAnnotation(AdminCommand.class) != null) {
        final CurrentUser u = currentUser.get();
        if (!u.isAdministrator()) {
          err.write("fatal: Not a Gerrit administrator\n".getBytes(ENC));
          err.flush();
          onExit(1);
          return;
        }
      }

      provideStateTo(cmd);
      if (cmd instanceof BaseCommand) {
        final BaseCommand bc = (BaseCommand) cmd;
        if (commandPrefix.isEmpty())
          bc.setCommandPrefix(name);
        else
          bc.setCommandPrefix(commandPrefix + " " + name);
        bc.setCommandLine(args);
      }
      cmd.start(env);
    } else {
      final String msg = prefix + ": " + name + ": not found\n";
      err.write(msg.getBytes(ENC));
      err.flush();
      onExit(127);
    }
  }

  private void usage() throws IOException, UnsupportedEncodingException {
    final StringBuilder usage = new StringBuilder();
    if (prefix.indexOf(' ') < 0) {
      usage.append("usage: " + prefix + " COMMAND [ARGS]\n");
    }
    usage.append("\n");
    usage.append("Available commands of " + prefix + " are:\n");
    usage.append("\n");
    for (Map.Entry<String, Provider<Command>> e : commands.entrySet()) {
      usage.append("   ");
      usage.append(e.getKey());
      usage.append("\n");
    }
    usage.append("\n");

    usage.append("See '");
    if (prefix.indexOf(' ') < 0) {
      usage.append(prefix);
      usage.append(' ');
    }
    usage.append("COMMAND --help' for more information.\n");
    usage.append("\n");
    err.write(usage.toString().getBytes("UTF-8"));
    err.flush();
    onExit(1);
  }
}
