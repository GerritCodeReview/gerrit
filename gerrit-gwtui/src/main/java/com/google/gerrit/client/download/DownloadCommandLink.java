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
import com.google.gerrit.client.info.DownloadInfo.DownloadCommandInfo;
import com.google.gwt.aria.client.Roles;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtexpui.clippy.client.CopyableLabel;

public class DownloadCommandLink extends Anchor implements ClickHandler {
  private final CopyableLabel copyLabel;
  private final String command;

  public DownloadCommandLink(CopyableLabel copyLabel, DownloadCommandInfo commandInfo) {
    super(commandInfo.name());
    this.copyLabel = copyLabel;
    this.command = commandInfo.command();

    setStyleName(Gerrit.RESOURCES.css().downloadLink());
    Roles.getTabRole().set(getElement());
    addClickHandler(this);
  }

  @Override
  public void onClick(ClickEvent event) {
    event.preventDefault();
    event.stopPropagation();

    select();
  }

  void select() {
    copyLabel.setText(command);

    DownloadCommandPanel parent = (DownloadCommandPanel) getParent();
    for (Widget w : parent) {
      if (w != this && w instanceof DownloadCommandLink) {
        w.removeStyleName(Gerrit.RESOURCES.css().downloadLink_Active());
      }
    }
    parent.setCurrentCommand(this);
    addStyleName(Gerrit.RESOURCES.css().downloadLink_Active());
  }
}
