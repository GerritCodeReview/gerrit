// Copyright (C) 2008 The Android Open Source Project
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

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences.DownloadCommand;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences.DownloadScheme;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwtexpui.clippy.client.CopyableLabel;

import java.util.Set;

public abstract class DownloadPanel extends FlowPanel {
  protected String projectName;

  protected Set<DownloadScheme> allowedSchemes =
      Gerrit.getConfig().getDownloadSchemes();
  protected Set<DownloadCommand> allowedCommands =
      Gerrit.getConfig().getDownloadCommands();
  protected DownloadCommandLink.CopyableCommandLinkFactory cmdLinkfactory;

  protected DownloadCommandPanel commands = new DownloadCommandPanel();
  protected DownloadUrlPanel urls = new DownloadUrlPanel(commands);
  protected CopyableLabel copyLabel = new CopyableLabel("");

  public DownloadPanel(String project, String ref, boolean allowAnonymous) {
    this.projectName = project;

    copyLabel.setStyleName(Gerrit.RESOURCES.css().downloadLinkCopyLabel());
    urls.add(DownloadUrlLink.createDownloadUrlLinks(project, ref, allowAnonymous));
    cmdLinkfactory = new DownloadCommandLink.CopyableCommandLinkFactory(
        copyLabel, urls);

    populateDownloadCommandLinks();
    setupWidgets();
  }

  protected void setupWidgets() {
    if (!commands.isEmpty()) {
      final AccountGeneralPreferences pref;
      if (Gerrit.isSignedIn()) {
        pref = Gerrit.getUserAccount().getGeneralPreferences();
      } else {
        pref = new AccountGeneralPreferences();
        pref.resetToDefaults();
      }
      commands.select(pref.getDownloadCommand());
      urls.select(pref.getDownloadUrl());

      FlowPanel p = new FlowPanel();
      p.setStyleName(Gerrit.RESOURCES.css().downloadLinkHeader());
      p.add(commands);
      final InlineLabel glue = new InlineLabel();
      glue.setStyleName(Gerrit.RESOURCES.css().downloadLinkHeaderGap());
      p.add(glue);
      p.add(urls);

      add(p);
      add(copyLabel);
    }
  }

  protected abstract void populateDownloadCommandLinks();
}
