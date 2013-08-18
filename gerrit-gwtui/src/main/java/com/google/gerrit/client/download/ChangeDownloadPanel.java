// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.client.download;

import com.google.gerrit.reviewdb.client.AccountGeneralPreferences.DownloadCommand;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences.DownloadScheme;
import com.google.gerrit.reviewdb.client.Change;

public class ChangeDownloadPanel extends DownloadPanel {
  private final int changeId;
  private final int patchSetId;

  public ChangeDownloadPanel(String project, String ref,
      boolean allowAnonymous, int changeId, int patchSetId) {
    super(project, ref, allowAnonymous);
    this.changeId = changeId;
    this.patchSetId = patchSetId;
  }

  @Override
  public void populateDownloadCommandLinks() {
    // This site prefers usage of the 'repo' tool, so suggest
    // that for easy fetch.
    //
    if (allowedSchemes.contains(DownloadScheme.REPO_DOWNLOAD)) {
      commands.add(cmdLinkfactory.new RepoCommandLink(projectName,
          changeId + "/" + patchSetId));
    }

    if (!urls.isEmpty()) {
      if (allowedCommands.contains(DownloadCommand.CHECKOUT)
          || allowedCommands.contains(DownloadCommand.DEFAULT_DOWNLOADS)) {
        commands.add(cmdLinkfactory.new CheckoutCommandLink());
      }
      if (allowedCommands.contains(DownloadCommand.PULL)
          || allowedCommands.contains(DownloadCommand.DEFAULT_DOWNLOADS)) {
        commands.add(cmdLinkfactory.new PullCommandLink());
      }
      if (allowedCommands.contains(DownloadCommand.CHERRY_PICK)
          || allowedCommands.contains(DownloadCommand.DEFAULT_DOWNLOADS)) {
        commands.add(cmdLinkfactory.new CherryPickCommandLink());
      }
      if (allowedCommands.contains(DownloadCommand.FORMAT_PATCH)
          || allowedCommands.contains(DownloadCommand.DEFAULT_DOWNLOADS)) {
        commands.add(cmdLinkfactory.new FormatPatchCommandLink());
      }
    }
  }
}