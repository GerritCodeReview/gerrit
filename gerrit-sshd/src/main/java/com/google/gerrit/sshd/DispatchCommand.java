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
import com.google.gerrit.sshd.args4j.SubcommandHandler;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;

import org.apache.sshd.server.Command;
import org.apache.sshd.server.Environment;
import org.kohsuke.args4j.Argument;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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
  private Command cmd;

  @Argument(index = 0, required = true, metaVar = "COMMAND", handler = SubcommandHandler.class)
  private String commandName;

  @Argument(index = 1, multiValued = true, metaVar = "ARG")
  private List<String> args = new ArrayList<String>();

  @Inject
  DispatchCommand(final Provider<CurrentUser> cu, @Assisted final String pfx,
      @Assisted final Map<String, Provider<Command>> all) {
    currentUser = cu;
    prefix = pfx;
    commands = all;
  }

  @Override
  public void start(final Environment env) throws IOException {
    try {
      parseCommandLine();

      final Provider<Command> p = commands.get(commandName);
      if (p == null) {
        String msg =
            (prefix.isEmpty() ? "Gerrit Code Review" : prefix) + ": "
                + commandName + ": not found";
        throw new UnloggedFailure(1, msg);
      }

      final Command cmd = p.get();

      if (isAdminCommand(cmd)
          && !currentUser.get().getCapabilities().canAdministrateServer()) {
        final String msg = "fatal: Not a Gerrit administrator";
        throw new UnloggedFailure(BaseCommand.STATUS_NOT_ADMIN, msg);
      }

      if (cmd instanceof BaseCommand) {
        final BaseCommand bc = (BaseCommand) cmd;
        if (prefix.isEmpty())
          bc.setName(commandName);
        else
          bc.setName(prefix + " " + commandName);
        bc.setArguments(args.toArray(new String[args.size()]));

      } else if (!args.isEmpty()) {
        throw new UnloggedFailure(1, commandName + " does not take arguments");
      }

      provideStateTo(cmd);

      synchronized (this) {
        this.cmd = cmd;
      }
      cmd.start(env);

    } catch (UnloggedFailure e) {
      String msg = e.getMessage();
      if (!msg.endsWith("\n")) {
        msg += "\n";
      }
      err.write(msg.getBytes(ENC));
      err.flush();
      onExit(e.exitCode);
    }
  }

  private boolean isAdminCommand(final Command cmd) {
    return cmd.getClass().getAnnotation(AdminCommand.class) != null;
  }

  @Override
  public void destroy() {
    synchronized (this) {
      if (cmd != null) {
        cmd.destroy();
        cmd = null;
      }
    }
  }

  @Override
  protected String usage() {
    final StringBuilder usage = new StringBuilder();
    usage.append("Available commands");
    if (!prefix.isEmpty()) {
      usage.append(" of ");
      usage.append(prefix);
    }
    usage.append(" are:\n");
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
    return usage.toString();
  }
}
