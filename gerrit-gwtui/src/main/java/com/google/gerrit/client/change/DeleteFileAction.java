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
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.event.logical.shared.CloseEvent;
import com.google.gwt.event.logical.shared.CloseHandler;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtexpui.globalkey.client.GlobalKey;

class DeleteFileAction {
  private final Project.NameKey project;
  private final Change.Id changeId;
  private final RevisionInfo revision;
  private final ChangeScreen.Style style;
  private final Widget deleteButton;

  private DeleteFileBox deleteBox;
  private PopupPanel popup;

  DeleteFileAction(
      Project.NameKey project,
      Change.Id changeId,
      RevisionInfo revision,
      ChangeScreen.Style style,
      Widget deleteButton) {
    this.project = project;
    this.changeId = changeId;
    this.revision = revision;
    this.style = style;
    this.deleteButton = deleteButton;
  }

  void onDelete() {
    if (popup != null) {
      popup.hide();
      return;
    }

    if (deleteBox == null) {
      deleteBox = new DeleteFileBox(project, changeId, revision);
    }
    deleteBox.clearPath();

    final PopupPanel p = new PopupPanel(true);
    p.setStyleName(style.replyBox());
    p.addAutoHidePartner(deleteButton.getElement());
    p.addCloseHandler(
        new CloseHandler<PopupPanel>() {
          @Override
          public void onClose(CloseEvent<PopupPanel> event) {
            if (popup == p) {
              popup = null;
            }
          }
        });
    p.add(deleteBox);
    p.showRelativeTo(deleteButton);
    GlobalKey.dialog(p);
    deleteBox.setFocus(true);
    popup = p;
  }
}
