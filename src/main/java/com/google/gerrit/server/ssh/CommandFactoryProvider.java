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

package com.google.gerrit.server.ssh;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;

import org.apache.sshd.server.CommandFactory;

/**
 * Creates a CommandFactory using commands registered by {@link CommandModule}.
 */
class CommandFactoryProvider implements Provider<CommandFactory> {
  private static final String SERVER = "Gerrit Code Review";
  private final DispatchCommandProvider dispatcher;

  @Inject
  CommandFactoryProvider(final Injector i) {
    dispatcher = new DispatchCommandProvider(i, SERVER, Commands.ROOT);
  }

  @Override
  public CommandFactory get() {
    return new CommandFactory() {
      public Command createCommand(final String requestCommand) {
        final DispatchCommand c = dispatcher.get();
        c.setCommandLine(requestCommand);
        return c;
      }
    };
  }
}
