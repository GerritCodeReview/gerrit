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

package com.google.gerrit.sshd.commands;

import com.google.gerrit.sshd.CommandName;
import com.google.gerrit.sshd.Commands;
import com.google.gerrit.sshd.DispatchCommandProvider;
import com.google.inject.AbstractModule;
import com.google.inject.binder.LinkedBindingBuilder;

import org.apache.sshd.server.Command;

public abstract class PluginCommandModule extends AbstractModule {
  private CommandName command;

  public void initSshModule(String pluginName) {
    command = Commands.named(pluginName);
    bind(Commands.key(command)).toProvider(new DispatchCommandProvider(command));
  }

  protected LinkedBindingBuilder<Command> command(String subCmd) {
    return bind(Commands.key(command, subCmd));
  }
}
