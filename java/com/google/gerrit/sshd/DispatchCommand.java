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
import com.google.common.base.Throwables;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Atomics;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.args4j.SubcommandHandler;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.sshd.server.Command;
import org.apache.sshd.server.Environment;
import org.kohsuke.args4j.Argument;

/** Command that dispatches to a subcommand from its command table. */
final class DispatchCommand extends BaseCommand {
  interface Factory {
    DispatchCommand create(Map<String, CommandProvider> map);
  }

  private final CurrentUser currentUser;
  private final PermissionBackend permissionBackend;
  private final Map<String, CommandProvider> commands;
  private final AtomicReference<Command> atomicCmd;

  @Argument(index = 0, required = false, metaVar = "COMMAND", handler = SubcommandHandler.class)
  private String commandName;

  @Argument(index = 1, multiValued = true, metaVar = "ARG")
  private List<String> args = new ArrayList<>();

  @Inject
  DispatchCommand(
      CurrentUser user,
      PermissionBackend permissionBackend,
      @Assisted Map<String, CommandProvider> all) {
    this.currentUser = user;
    this.permissionBackend = permissionBackend;
    commands = all;
    atomicCmd = Atomics.newReference();
  }

  Map<String, CommandProvider> getMap() {
    return commands;
  }

  @Override
  public void start(Environment env) throws IOException {
    try {
      parseCommandLine();
      if (Strings.isNullOrEmpty(commandName)) {
        StringWriter msg = new StringWriter();
        msg.write(usage());
        throw die(msg.toString());
      }

      final CommandProvider p = commands.get(commandName);
      if (p == null) {
        String msg =
            (getName().isEmpty() ? "Gerrit Code Review" : getName())
                + ": "
                + commandName
                + ": not found";
        throw die(msg);
      }

      final Command cmd = p.getProvider().get();
      checkRequiresCapability(cmd);
      if (cmd instanceof BaseCommand) {
        final BaseCommand bc = (BaseCommand) cmd;
        if (getName().isEmpty()) {
          bc.setName(commandName);
        } else {
          bc.setName(getName() + " " + commandName);
        }
        bc.setArguments(args.toArray(new String[args.size()]));

      } else if (!args.isEmpty()) {
        throw die(commandName + " does not take arguments");
      }

      provideStateTo(cmd);
      atomicCmd.set(cmd);
      cmd.start(env);

      if (cmd instanceof BaseCommand) {
        sensitiveParameters = ((BaseCommand) cmd).sensitiveParameters;
        setMaskedArguments(((BaseCommand) cmd).maskSensitiveParameters(getArguments()));
      }

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

  private void checkRequiresCapability(Command cmd) throws UnloggedFailure {
    String pluginName = null;
    if (cmd instanceof BaseCommand) {
      pluginName = ((BaseCommand) cmd).getPluginName();
    }
    try {
      permissionBackend
          .user(currentUser)
          .checkAny(GlobalPermission.fromAnnotation(pluginName, cmd.getClass()));
    } catch (AuthException e) {
      throw new UnloggedFailure(BaseCommand.STATUS_NOT_ADMIN, e.getMessage());
    } catch (PermissionBackendException e) {
      throw new UnloggedFailure(1, "fatal: permission check unavailable", e);
    }
  }

  @Override
  public void destroy() {
    Command cmd = atomicCmd.getAndSet(null);
    if (cmd != null) {
      try {
        cmd.destroy();
      } catch (Exception e) {
        Throwables.throwIfUnchecked(e);
        throw new RuntimeException(e);
      }
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
      usage.append("   ");
      usage.append(String.format(format, name, Strings.nullToEmpty(p.getDescription())));
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
