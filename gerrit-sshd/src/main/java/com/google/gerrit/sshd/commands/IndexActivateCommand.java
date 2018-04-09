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

import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.server.index.AbstractVersionManager;
import com.google.gerrit.server.index.ReindexerAlreadyRunningException;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;
import org.kohsuke.args4j.Argument;

@RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
@CommandMetaData(name = "activate", description = "Activate the latest index version available")
public class IndexActivateCommand extends SshCommand {

  @Argument(index = 0, required = true, metaVar = "INDEX", usage = "index name to activate")
  private String name;

  @Inject private AbstractVersionManager versionManager;

  @Override
  protected void run() throws UnloggedFailure {
    try {
      if (versionManager.isKnownIndex(name)) {
        if (versionManager.activateLatestIndex(name)) {
          stdout.println("Activated latest index version");
        } else {
          stdout.println("Not activating index, already using latest version");
        }
      } else {
        stderr.println("Cannot activate index, unknown based on this name");
      }
    } catch (ReindexerAlreadyRunningException e) {
      throw die("Failed to activate latest index: " + e.getMessage());
    }
  }
}
