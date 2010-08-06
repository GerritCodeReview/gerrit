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
import com.google.gwt.user.client.ui.Accessibility;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;

class DownloadUrlPanel extends FlowPanel {
  private final DownloadCommandPanel commandPanel;

  DownloadUrlPanel(final DownloadCommandPanel commandPanel) {
    this.commandPanel = commandPanel;
    setStyleName(Gerrit.RESOURCES.css().downloadLinkList());
    Accessibility.setRole(getElement(), Accessibility.ROLE_TABLIST);
  }

  boolean isEmpty() {
    return getWidgetCount() == 0;
  }

  void select(AccountGeneralPreferences.DownloadScheme urlType) {
    DownloadUrlLink first = null;

    for (Widget w : this) {
      if (w instanceof DownloadUrlLink) {
        final DownloadUrlLink d = (DownloadUrlLink) w;
        if (first == null) {
          first = d;
        }
        if (d.urlType == urlType) {
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
    commandPanel.setCurrentUrl(link);
  }
}
