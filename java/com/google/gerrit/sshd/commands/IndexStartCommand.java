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

import static com.google.gerrit.server.i18n.I18n.getText;

import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.server.index.ReindexerAlreadyRunningException;
import com.google.gerrit.server.index.VersionManager;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

@RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
@CommandMetaData(name = "start", description = "Start the online reindexer")
public class IndexStartCommand extends SshCommand {

  @Option(name = "--force", usage = "force a re-index")
  private boolean force;

  @Argument(index = 0, required = true, metaVar = "INDEX", usage = "index name to start")
  private String name;

  @Inject private VersionManager versionManager;

  @Override
  protected void run() throws UnloggedFailure {
    try {
      if (versionManager.isKnownIndex(name)) {
        if (versionManager.startReindexer(name, force)) {
          stdout.println(getText("sshd.command.index.start.reindexer.started"));
        } else {
          stdout.println(getText("sshd.command.index.start.reindexer.already.latest.version"));
        }
      } else {
        stderr.println(getText("ssh.command.index.start.reindexer.unknown.index", name));
      }
    } catch (ReindexerAlreadyRunningException e) {
      throw die(getText("sshd.command.index.start.reindexer.failed", e.getMessage()));
    }
  }
}
