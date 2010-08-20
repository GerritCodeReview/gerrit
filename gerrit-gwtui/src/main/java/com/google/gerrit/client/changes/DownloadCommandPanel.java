// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.client.changes;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.reviewdb.AccountGeneralPreferences;
import com.google.gerrit.reviewdb.AccountGeneralPreferences.DownloadCommand;
import com.google.gwt.user.client.ui.Accessibility;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;

class DownloadCommandPanel extends FlowPanel {
  private DownloadCommandLink currentCommand;
  private DownloadUrlLink currentUrl;

  DownloadCommandPanel() {
    setStyleName(Gerrit.RESOURCES.css().downloadLinkList());
    Accessibility.setRole(getElement(), Accessibility.ROLE_TABLIST);
  }

  boolean isEmpty() {
    return getWidgetCount() == 0;
  }

  void select(AccountGeneralPreferences.DownloadCommand cmdType) {
    DownloadCommandLink first = null;

    for (Widget w : this) {
      if (w instanceof DownloadCommandLink) {
        final DownloadCommandLink d = (DownloadCommandLink) w;
        if (first == null) {
          first = d;
        }
        if (d.cmdType == cmdType) {
          d.select();
          return;
        }
      }
    }

    // If none matched the requested type, select the first in the
    // group as that will at least give us an initial baseline.
    if (first != null) {
      first.select();
    }
  }

  void setCurrentUrl(DownloadUrlLink link) {
    currentUrl = link;
    update();
  }

  void setCurrentCommand(DownloadCommandLink cmd) {
    currentCommand = cmd;
    update();
  }

  private void update() {
    if (currentCommand != null && currentUrl != null) {
      currentCommand.setCurrentUrl(currentUrl);
    } else if (currentCommand != null &&
        currentCommand.getCmdType().equals(DownloadCommand.REPO_DOWNLOAD)) {
      currentCommand.setCurrentUrl(null);
    }
  }
}
