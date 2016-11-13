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
import com.google.gerrit.client.info.DownloadInfo.DownloadCommandInfo;
import com.google.gerrit.client.info.DownloadInfo.DownloadSchemeInfo;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwtexpui.clippy.client.CopyableLabel;
import java.util.List;

public abstract class DownloadPanel extends FlowPanel {
  protected final String project;

  private final DownloadCommandPanel commands = new DownloadCommandPanel();
  private final DownloadUrlPanel urls = new DownloadUrlPanel();
  private final CopyableLabel copyLabel = new CopyableLabel("");

  public DownloadPanel(String project, boolean allowAnonymous) {
    this.project = project;
    copyLabel.setStyleName(Gerrit.RESOURCES.css().downloadLinkCopyLabel());
    urls.add(DownloadUrlLink.createDownloadUrlLinks(allowAnonymous, this));

    setupWidgets();
  }

  private void setupWidgets() {
    if (!urls.isEmpty()) {
      urls.select(Gerrit.getUserPreferences().downloadScheme());

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

  void populateDownloadCommandLinks(DownloadSchemeInfo schemeInfo) {
    commands.clear();
    for (DownloadCommandInfo cmd : getCommands(schemeInfo)) {
      commands.add(new DownloadCommandLink(copyLabel, cmd));
    }
    commands.select();
  }

  protected abstract List<DownloadCommandInfo> getCommands(DownloadSchemeInfo schemeInfo);
}
