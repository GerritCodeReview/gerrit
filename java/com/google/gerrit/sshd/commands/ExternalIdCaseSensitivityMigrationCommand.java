// Copyright (C) 2021 The Android Open Source Project
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

import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.server.account.externalids.storage.notedb.OnlineExternalIdCaseSensivityMigrator;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;
import org.eclipse.jgit.lib.Config;

@RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
@CommandMetaData(
    name = "migrate-externalids-to-insensitive",
    description = "Migrate external-ids to case insensitive")
public class ExternalIdCaseSensitivityMigrationCommand extends SshCommand {

  @Inject OnlineExternalIdCaseSensivityMigrator onlineExternalIdCaseSensivityMigrator;
  @Inject @GerritServerConfig private Config globalConfig;

  @Override
  public void run() throws UnloggedFailure, Failure, Exception {
    Boolean isUserNameCaseInsensitiveMigrationMode =
        globalConfig.getBoolean("auth", "userNameCaseInsensitiveMigrationMode", false);
    Boolean isUserNameCaseInsensitive =
        globalConfig.getBoolean("auth", "userNameCaseInsensitive", false);

    if (!isUserNameCaseInsensitive || !isUserNameCaseInsensitiveMigrationMode) {
      die(
          "External IDs online migration requires auth.userNameCaseInsensitive and"
              + " auth.userNameCaseInsensitiveMigrationMode to be set to true. Cannot start"
              + " migration!");
    }
    onlineExternalIdCaseSensivityMigrator.migrate();
    stdout.println(
        "External ids case insensitivity migration started. To check if it's completed look for"
            + " \"External IDs migration completed!\" message in the Gerrit server logs");
  }
}
