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
import com.google.gerrit.server.plugins.InvalidPluginException;
import com.google.gerrit.server.plugins.PluginInstallException;
import com.google.gerrit.server.plugins.PluginLoader;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;
import java.util.List;
import org.kohsuke.args4j.Argument;

@RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
@CommandMetaData(name = "reload", description = "Reload/Restart plugins", runsAt = MASTER_OR_SLAVE)
final class PluginReloadCommand extends SshCommand {
  @Argument(index = 0, metaVar = "NAME", usage = "plugins to reload/restart")
  private List<String> names;

  @Inject private PluginLoader loader;

  @Override
  protected void run() throws UnloggedFailure {
    if (!loader.isRemoteAdminEnabled()) {
      throw die("remote plugin administration is disabled");
    }
    if (names == null || names.isEmpty()) {
      loader.rescan();
    } else {
      try {
        loader.reload(names);
      } catch (InvalidPluginException | PluginInstallException e) {
        throw die(e.getMessage());
      }
    }
  }
}
