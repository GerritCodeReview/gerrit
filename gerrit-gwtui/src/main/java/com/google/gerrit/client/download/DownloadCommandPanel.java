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

package com.google.gerrit.client.download;

import com.google.gerrit.client.Gerrit;
import com.google.gwt.aria.client.Roles;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;

public class DownloadCommandPanel extends FlowPanel {
  private DownloadCommandLink currentCommand;

  public DownloadCommandPanel() {
    setStyleName(Gerrit.RESOURCES.css().downloadLinkList());
    Roles.getTablistRole().set(getElement());
  }

  public boolean isEmpty() {
    return getWidgetCount() == 0;
  }

  public void select() {
    DownloadCommandLink first = null;

    for (Widget w : this) {
      if (w instanceof DownloadCommandLink) {
        DownloadCommandLink d = (DownloadCommandLink) w;
        if (currentCommand != null && d.getText().equals(currentCommand.getText())) {
          d.select();
          return;
        }
        if (first == null) {
          first = d;
        }
      }
    }

    // If none matched the requested type, select the first in the
    // group as that will at least give us an initial baseline.
    if (first != null) {
      first.select();
    }
  }

  void setCurrentCommand(DownloadCommandLink cmd) {
    currentCommand = cmd;
  }
}
