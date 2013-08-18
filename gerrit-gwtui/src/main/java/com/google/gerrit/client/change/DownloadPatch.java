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

package com.google.gerrit.client.change;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.changes.ChangeInfo;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Image;

class DownloadPatch extends Image implements ClickHandler {
  private Change.Id changeId;
  private String revision;

  DownloadPatch() {
    setResource(Gerrit.RESOURCES.downloadIcon());
    addClickHandler(this);
  }

  void set(ChangeInfo info, String revision) {
    this.changeId = info.legacy_id();
    this.revision = revision;
  }

  @Override
  public void onClick(ClickEvent e) {
    StringBuilder url = new StringBuilder(GWT.getHostPageBaseURL())
        .append("changes/")
        .append(changeId.toString())
        .append("/revisions/")
        .append(revision)
        .append("/patch?download");
    Window.open(url.toString(), "", "");
  }
}
