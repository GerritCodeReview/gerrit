// Copyright (C) 2013 The Android Open Source Project
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
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.inject.Inject;
import com.google.inject.binder.LinkedBindingBuilder;

import org.apache.sshd.server.Command;

/**
 * Binds one SSH command to the plugin name itself.
 * <p>
 * Cannot be combined with {@link PluginCommandModule}.
 */
public abstract class SingleCommandPluginModule extends CommandModule {
  private CommandName command;

  @Inject
  void setPluginName(@PluginName String name) {
    this.command = Commands.named(name);
  }

  @Override
  protected final void configure() {
    Preconditions.checkState(command != null, "@PluginName must be provided");
    configure(bind(Commands.key(command)));
  }

  protected abstract void configure(LinkedBindingBuilder<Command> bind);
}
