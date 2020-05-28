// Copyright (C) 2012 The Android Open Source Project
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

import com.google.common.base.Throwables;
import com.google.common.util.concurrent.Atomics;
import com.google.gerrit.extensions.api.access.GlobalOrPluginPermission;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;

/** Command that executes some other command. */
public class AliasCommand extends BaseCommand {
  private final DispatchCommandProvider root;
  private final PermissionBackend permissionBackend;
  private final CommandName command;
  private final AtomicReference<Command> atomicCmd;

  AliasCommand(
      @CommandName(Commands.ROOT) DispatchCommandProvider root,
      PermissionBackend permissionBackend,
      CommandName command) {
    this.root = root;
    this.permissionBackend = permissionBackend;
    this.command = command;
    this.atomicCmd = Atomics.newReference();
  }

  @Override
  public void start(ChannelSession channel, Environment env) throws IOException {
    try {
      begin(channel, env);
    } catch (Failure e) {
      String msg = e.getMessage();
      if (!msg.endsWith("\n")) {
        msg += "\n";
      }
      err.write(msg.getBytes(ENC));
      err.flush();
      onExit(e.exitCode);
    }
  }

  private void begin(ChannelSession channel, Environment env) throws IOException, Failure {
    Map<String, CommandProvider> map = root.getMap();
    for (String name : chain(command)) {
      CommandProvider p = map.get(name);
      if (p == null) {
        throw die(getName() + ": not found");
      }

      Command cmd = p.getProvider().get();
      if (!(cmd instanceof DispatchCommand)) {
        throw die(getName() + ": not found");
      }
      map = ((DispatchCommand) cmd).getMap();
    }

    CommandProvider p = map.get(command.value());
    if (p == null) {
      throw die(getName() + ": not found");
    }

    Command cmd = p.getProvider().get();
    checkRequiresCapability(cmd);
    if (cmd instanceof BaseCommand) {
      BaseCommand bc = (BaseCommand) cmd;
      bc.setName(getName());
      bc.setArguments(getArguments());
    }
    provideStateTo(cmd);
    atomicCmd.set(cmd);
    cmd.start(channel, env);
  }

  @Override
  public void destroy(ChannelSession channel) {
    Command cmd = atomicCmd.getAndSet(null);
    if (cmd != null) {
      try {
        cmd.destroy(channel);
      } catch (Exception e) {
        Throwables.throwIfUnchecked(e);
        throw new RuntimeException(e);
      }
    }
  }

  private void checkRequiresCapability(Command cmd) throws Failure {
    try {
      Set<GlobalOrPluginPermission> check = GlobalPermission.fromAnnotation(cmd.getClass());
      try {
        permissionBackend.currentUser().checkAny(check);
      } catch (AuthException err) {
        throw new UnloggedFailure(BaseCommand.STATUS_NOT_ADMIN, "fatal: " + err.getMessage());
      }
    } catch (PermissionBackendException err) {
      throw new Failure(1, "fatal: permissions unavailable", err);
    }
  }

  private static LinkedList<String> chain(CommandName command) {
    LinkedList<String> chain = new LinkedList<>();
    while (command != null) {
      chain.addFirst(command.value());
      command = Commands.parentOf(command);
    }
    chain.removeLast();
    return chain;
  }
}
