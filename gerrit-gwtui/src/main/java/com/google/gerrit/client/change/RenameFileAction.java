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

package com.google.gerrit.client.change;

import com.google.gerrit.client.info.ChangeInfo.RevisionInfo;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gwt.event.logical.shared.CloseEvent;
import com.google.gwt.event.logical.shared.CloseHandler;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtexpui.globalkey.client.GlobalKey;

class RenameFileAction {
  private final Change.Id changeId;
  private final RevisionInfo revision;
  private final ChangeScreen.Style style;
  private final Widget renameButton;

  private RenameFileBox renameBox;
  private PopupPanel popup;

  RenameFileAction(
      Change.Id changeId, RevisionInfo revision, ChangeScreen.Style style, Widget renameButton) {
    this.changeId = changeId;
    this.revision = revision;
    this.style = style;
    this.renameButton = renameButton;
  }

  void onRename() {
    if (popup != null) {
      popup.hide();
      return;
    }

    if (renameBox == null) {
      renameBox = new RenameFileBox(changeId, revision);
    }
    renameBox.clearPath();

    final PopupPanel p = new PopupPanel(true);
    p.setStyleName(style.replyBox());
    p.addAutoHidePartner(renameButton.getElement());
    p.addCloseHandler(
        new CloseHandler<PopupPanel>() {
          @Override
          public void onClose(CloseEvent<PopupPanel> event) {
            if (popup == p) {
              popup = null;
            }
          }
        });
    p.add(renameBox);
    p.showRelativeTo(renameButton);
    GlobalKey.dialog(p);
    renameBox.setFocus(true);
    popup = p;
  }
}
