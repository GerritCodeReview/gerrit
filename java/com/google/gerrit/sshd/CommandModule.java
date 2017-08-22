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

import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.inject.binder.LinkedBindingBuilder;
import org.apache.sshd.server.Command;

/** Module to register commands in the SSH daemon. */
public abstract class CommandModule extends LifecycleModule {
  protected boolean slaveMode;

  /**
   * Configure a command to be invoked by name.
   *
   * @param name the name of the command the client will provide in order to call the command.
   * @return a binding that must be bound to a non-singleton provider for a {@link Command} object.
   */
  protected LinkedBindingBuilder<Command> command(String name) {
    return bind(Commands.key(name));
  }

  /**
   * Configure a command to be invoked by name.
   *
   * @param name the name of the command the client will provide in order to call the command.
   * @return a binding that must be bound to a non-singleton provider for a {@link Command} object.
   */
  protected LinkedBindingBuilder<Command> command(CommandName name) {
    return bind(Commands.key(name));
  }

  /**
   * Configure a command to be invoked by name.
   *
   * @param parent context of the parent command, that this command is a subcommand of.
   * @param name the name of the command the client will provide in order to call the command.
   * @return a binding that must be bound to a non-singleton provider for a {@link Command} object.
   */
  protected LinkedBindingBuilder<Command> command(CommandName parent, String name) {
    return bind(Commands.key(parent, name));
  }

  /**
   * Configure a command to be invoked by name. The command is bound to the passed class.
   *
   * @param parent context of the parent command, that this command is a subcommand of.
   * @param clazz class of the command with {@link CommandMetaData} annotation to retrieve the name
   *     and the description from
   */
  protected void command(CommandName parent, Class<? extends BaseCommand> clazz) {
    CommandMetaData meta = clazz.getAnnotation(CommandMetaData.class);
    if (meta == null) {
      throw new IllegalStateException("no CommandMetaData annotation found");
    }
    if (meta.runsAt().isSupported(slaveMode)) {
      bind(Commands.key(parent, meta.name(), meta.description())).to(clazz);
    }
  }

  /**
   * Alias one command to another. The alias is bound to the passed class.
   *
   * @param parent context of the parent command, that this command is a subcommand of.
   * @param name the name of the command the client will provide in order to call the command.
   * @param clazz class of the command with {@link CommandMetaData} annotation to retrieve the
   *     description from
   */
  protected void alias(final CommandName parent, String name, Class<? extends BaseCommand> clazz) {
    CommandMetaData meta = clazz.getAnnotation(CommandMetaData.class);
    if (meta == null) {
      throw new IllegalStateException("no CommandMetaData annotation found");
    }
    bind(Commands.key(parent, name, meta.description())).to(clazz);
  }

  /**
   * Alias one command to another.
   *
   * @param from the new command name that when called will actually delegate to {@code to}'s
   *     implementation.
   * @param to name of an already registered command that will perform the action when {@code from}
   *     is invoked by a client.
   */
  protected void alias(String from, String to) {
    bind(Commands.key(from)).to(Commands.key(to));
  }
}
