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

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.gerrit.extensions.annotations.Export;
import com.google.gerrit.server.plugins.InvalidPluginException;
import com.google.gerrit.server.plugins.ModuleGenerator;
import com.google.inject.AbstractModule;
import com.google.inject.Module;

import org.apache.sshd.server.Command;

import java.util.Map;

class SshAutoRegisterModuleGenerator
    extends AbstractModule
    implements ModuleGenerator {
  private final Map<String, Class<Command>> commands = Maps.newHashMap();
  private CommandName command;

  @Override
  protected void configure() {
    bind(Commands.key(command))
        .toProvider(new DispatchCommandProvider(command));
    for (Map.Entry<String, Class<Command>> e : commands.entrySet()) {
      bind(Commands.key(command, e.getKey())).to(e.getValue());
    }
  }

  public void setPluginName(String name) {
    command = Commands.named(name);
  }

  @SuppressWarnings("unchecked")
  @Override
  public void export(Export export, Class<?> type)
      throws InvalidPluginException {
    Preconditions.checkState(command != null, "pluginName must be provided");
    if (Command.class.isAssignableFrom(type)) {
      Class<Command> old = commands.get(export.value());
      if (old != null) {
        throw new InvalidPluginException(String.format(
            "@Export(\"%s\") has duplicate bindings:\n  %s\n  %s",
            export.value(), old.getName(), type.getName()));
      }
      commands.put(export.value(), (Class<Command>) type);
    } else {
      throw new InvalidPluginException(String.format(
          "Class %s with @Export(\"%s\") must extend %s or implement %s",
          type.getName(), export.value(),
          SshCommand.class.getName(), Command.class.getName()));
    }
  }

  @Override
  public Module create() throws InvalidPluginException {
    Preconditions.checkState(command != null, "pluginName must be provided");
    return this;
  }
}
