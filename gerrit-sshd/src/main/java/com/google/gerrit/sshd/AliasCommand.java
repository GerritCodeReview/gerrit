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

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Atomics;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.CapabilityControl;
import com.google.inject.Provider;

import org.apache.sshd.server.Command;
import org.apache.sshd.server.Environment;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/** Command that executes some other command. */
public class AliasCommand extends BaseCommand {
  private final DispatchCommandProvider root;
  private final Provider<CurrentUser> currentUser;
  private final CommandName command;
  private final AtomicReference<Command> atomicCmd;

  AliasCommand(@CommandName(Commands.ROOT) DispatchCommandProvider root,
      Provider<CurrentUser> currentUser, CommandName command) {
    this.root = root;
    this.currentUser = currentUser;
    this.command = command;
    this.atomicCmd = Atomics.newReference();
  }

  @Override
  public void start(Environment env) throws IOException {
    try {
      begin(env);
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

  private void begin(Environment env) throws UnloggedFailure, IOException {
    Map<String, Provider<Command>> map = root.getMap();
    for (String name : chain(command)) {
      Provider<? extends Command> p = map.get(name);
      if (p == null) {
        throw new UnloggedFailure(1, getName() + ": not found");
      }

      Command cmd = p.get();
      if (!(cmd instanceof DispatchCommand)) {
        throw new UnloggedFailure(1, getName() + ": not found");
      }
      map = ((DispatchCommand) cmd).getMap();
    }

    Provider<? extends Command> p = map.get(command.value());
    if (p == null) {
      throw new UnloggedFailure(1, getName() + ": not found");
    }

    Command cmd = p.get();
    checkRequiresCapability(cmd);
    if (cmd instanceof BaseCommand) {
      BaseCommand bc = (BaseCommand)cmd;
      bc.setName(getName());
      bc.setArguments(getArguments());
    }
    provideStateTo(cmd);
    atomicCmd.set(cmd);
    cmd.start(env);
  }

  @Override
  public void destroy() {
    Command cmd = atomicCmd.getAndSet(null);
    if (cmd != null) {
        cmd.destroy();
    }
  }

  private void checkRequiresCapability(Command cmd) throws UnloggedFailure {
    RequiresCapability rc = cmd.getClass().getAnnotation(RequiresCapability.class);
    if (rc != null) {
      CurrentUser user = currentUser.get();
      CapabilityControl ctl = user.getCapabilities();
      if (!ctl.canPerform(rc.value()) && !ctl.canAdministrateServer()) {
        String msg = String.format(
            "fatal: %s does not have \"%s\" capability.",
            user.getUserName(), rc.value());
        throw new UnloggedFailure(BaseCommand.STATUS_NOT_ADMIN, msg);
      }
    }
  }

  private static LinkedList<String> chain(CommandName command) {
    LinkedList<String> chain = Lists.newLinkedList();
    while (command != null) {
      chain.addFirst(command.value());
      command = Commands.parentOf(command);
    }
    chain.removeLast();
    return chain;
  }
}
