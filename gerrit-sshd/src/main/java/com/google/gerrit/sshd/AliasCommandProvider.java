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

import com.google.gerrit.server.CurrentUser;
import com.google.inject.Inject;
import com.google.inject.Provider;
import org.apache.sshd.server.Command;

/** Resolves an alias to another command. */
public class AliasCommandProvider implements Provider<Command> {
  private final CommandName command;

  @Inject
  @CommandName(Commands.ROOT)
  private DispatchCommandProvider root;

  @Inject private CurrentUser currentUser;

  public AliasCommandProvider(CommandName command) {
    this.command = command;
  }

  @Override
  public Command get() {
    return new AliasCommand(root, currentUser, command);
  }
}
