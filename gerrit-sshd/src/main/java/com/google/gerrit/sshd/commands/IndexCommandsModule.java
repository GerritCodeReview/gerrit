// Copyright (C) 2015 The Android Open Source Project
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

import com.google.gerrit.server.index.VersionManager;
import com.google.gerrit.sshd.CommandModule;
import com.google.gerrit.sshd.CommandName;
import com.google.gerrit.sshd.Commands;
import com.google.gerrit.sshd.DispatchCommandProvider;
import com.google.inject.Injector;
import com.google.inject.Key;

public class IndexCommandsModule extends CommandModule {

  private final Injector injector;

  public IndexCommandsModule(Injector injector) {
    this.injector = injector;
  }

  @Override
  protected void configure() {
    CommandName gerrit = Commands.named("gerrit");
    CommandName index = Commands.named(gerrit, "index");
    command(index).toProvider(new DispatchCommandProvider(index));
    if (injector.getExistingBinding(Key.get(VersionManager.class)) != null) {
      command(index, IndexActivateCommand.class);
      command(index, IndexStartCommand.class);
    }
    command(index, IndexChangesCommand.class);
    command(index, IndexProjectCommand.class);
  }
}
