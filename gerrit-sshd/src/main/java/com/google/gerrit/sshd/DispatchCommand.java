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

import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Atomics;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.CapabilityUtils;
import com.google.gerrit.server.args4j.SubcommandHandler;
import com.google.gerrit.sshd.commands.ErrorSlaveMode;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;

import org.apache.sshd.server.Command;
import org.apache.sshd.server.Environment;
import org.kohsuke.args4j.Argument;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Command that dispatches to a subcommand from its command table.
 */
final class DispatchCommand extends BaseCommand {
  interface Factory {
    DispatchCommand create(Map<String, CommandProvider> map);
  }

  private final Provider<CurrentUser> currentUser;
  private final Map<String, CommandProvider> commands;
  private final AtomicReference<Command> atomicCmd;

  @Argument(index = 0, required = false, metaVar = "COMMAND", handler = SubcommandHandler.class)
  private String commandName;

  @Argument(index = 1, multiValued = true, metaVar = "ARG")
  private List<String> args = new ArrayList<String>();

  @Inject
  DispatchCommand(final Provider<CurrentUser> cu,
      @Assisted final Map<String, CommandProvider> all) {
    currentUser = cu;
    commands = all;
    atomicCmd = Atomics.newReference();
  }

  Map<String, CommandProvider> getMap() {
    return commands;
  }

  @Override
  public void start(final Environment env) throws IOException {
    try {
      parseCommandLine();
      if (Strings.isNullOrEmpty(commandName)) {
        StringWriter msg = new StringWriter();
        msg.write(usage());
        throw new UnloggedFailure(1, msg.toString());
      }

      final CommandProvider p = commands.get(commandName);
      if (p == null) {
        String msg =
            (getName().isEmpty() ? "Gerrit Code Review" : getName()) + ": "
                + commandName + ": not found";
        throw new UnloggedFailure(1, msg);
      }

      final Command cmd = p.getProvider().get();
      checkRequiresCapability(cmd);
      if (cmd instanceof BaseCommand) {
        final BaseCommand bc = (BaseCommand) cmd;
        if (getName().isEmpty())
          bc.setName(commandName);
        else
          bc.setName(getName() + " " + commandName);
        bc.setArguments(args.toArray(new String[args.size()]));

      } else if (!args.isEmpty()) {
        throw new UnloggedFailure(1, commandName + " does not take arguments");
      }

      provideStateTo(cmd);
      atomicCmd.set(cmd);
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

  private void checkRequiresCapability(Command cmd)
      throws UnloggedFailure {
    String pluginName = null;
    if (cmd instanceof BaseCommand) {
      pluginName = ((BaseCommand) cmd).getPluginName();
    }
    try {
      CapabilityUtils.checkRequiresCapability(currentUser,
          pluginName, cmd.getClass());
    } catch (AuthException e) {
      throw new UnloggedFailure(BaseCommand.STATUS_NOT_ADMIN,
          e.getMessage());
    }
  }

  @Override
  public void destroy() {
    Command cmd = atomicCmd.getAndSet(null);
    if (cmd != null) {
        cmd.destroy();
    }
  }

  @Override
  protected String usage() {
    final StringBuilder usage = new StringBuilder();
    usage.append("Available commands");
    if (!getName().isEmpty()) {
      usage.append(" of ");
      usage.append(getName());
    }
    usage.append(" are:\n");
    usage.append("\n");

    int maxLength = -1;
    for (String name : commands.keySet()) {
      maxLength = Math.max(maxLength, name.length());
    }
    String format = "%-" + maxLength + "s   %s";
    for (String name : Sets.newTreeSet(commands.keySet())) {
      final CommandProvider p = commands.get(name);
      Command c = p.getProvider().get();
      String description = c instanceof ErrorSlaveMode
          ? "Command disabled: server is running in slave mode"
          : Strings.nullToEmpty(p.getDescription());

      usage.append("   ");
      usage.append(String.format(format, name, description));
      usage.append("\n");
    }
    usage.append("\n");

    usage.append("See '");
    if (getName().indexOf(' ') < 0) {
      usage.append(getName());
      usage.append(' ');
    }
    usage.append("COMMAND --help' for more information.\n");
    usage.append("\n");
    return usage.toString();
  }

  public String getCommandName() {
    return commandName;
  }
}
