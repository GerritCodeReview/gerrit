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

import static com.google.gerrit.sshd.CommandMetaData.Mode.MASTER_OR_SLAVE;

import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.server.plugins.ListPlugins;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;

@RequiresCapability(GlobalCapability.VIEW_PLUGINS)
@CommandMetaData(name = "ls", description = "List the installed plugins", runsAt = MASTER_OR_SLAVE)
final class PluginLsCommand extends SshCommand {
  @Inject private ListPlugins impl;

  @Override
  public void run() throws Exception {
    impl.display(stdout);
  }

  @Override
  protected void parseCommandLine() throws UnloggedFailure {
    parseCommandLine(impl);
  }
}
