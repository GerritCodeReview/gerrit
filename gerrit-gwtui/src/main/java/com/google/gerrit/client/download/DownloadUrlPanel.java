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
import java.util.Collection;

public class DownloadUrlPanel extends FlowPanel {

  public DownloadUrlPanel() {
    setStyleName(Gerrit.RESOURCES.css().downloadLinkList());
    Roles.getTablistRole().set(getElement());
  }

  public boolean isEmpty() {
    return getWidgetCount() == 0;
  }

  public void select(String schemeName) {
    DownloadUrlLink first = null;

    for (Widget w : this) {
      if (w instanceof DownloadUrlLink) {
        final DownloadUrlLink d = (DownloadUrlLink) w;
        if (first == null) {
          first = d;
        }
        if (d.getSchemeName().equals(schemeName)) {
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

  public void add(Collection<DownloadUrlLink> links) {
    for (Widget link : links) {
      add(link);
    }
  }
}
