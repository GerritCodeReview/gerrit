// Copyright (C) 2018 The Android Open Source Project
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
import com.google.gerrit.server.config.ConfigUpdatedEvent;
import com.google.gerrit.server.config.ConfigUpdatedEvent.UpdateResult;
import com.google.gerrit.server.config.GerritServerConfigReloader;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;

/** Issues a reload of gerrit.config. */
@RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
@CommandMetaData(
  name = "reload-config",
  description = "Reloads the Gerrit configuration",
  runsAt = MASTER_OR_SLAVE
)
public class ReloadConfig extends SshCommand {

  @Inject private GerritServerConfigReloader gerritServerConfigReloader;

  @Override
  protected void run() throws Failure {
    List<ConfigUpdatedEvent.Update> updates = gerritServerConfigReloader.reloadConfig();
    if (updates.isEmpty()) {
      stdout.println("No reloadable config entries updated!");
      return;
    }

    // Print out UpdateResult.{ACCEPTED|REJECTED} entries grouped by their type
    for (UpdateResult updateResult : UpdateResult.values()) {
      List<ConfigUpdatedEvent.Update> filteredUpdates = filterUpdates(updates, updateResult);
      if (filteredUpdates.isEmpty()) {
        continue;
      }
      stdout.println(updateResult.toString() + " configuration changes:");
      filteredUpdates
          .stream()
          .flatMap(update -> update.getConfigUpdates().stream())
          .forEach(cfgEntry -> stdout.println(cfgEntry.toString()));
    }
  }

  public static List<ConfigUpdatedEvent.Update> filterUpdates(
      List<ConfigUpdatedEvent.Update> updates, UpdateResult result) {
    return updates
        .stream()
        .filter(update -> update.getResult() == result)
        .collect(Collectors.toList());
  }
}
