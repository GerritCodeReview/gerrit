// Copyright (C) 2017 The Android Open Source Project
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
import com.google.gerrit.client.changes.ChangeApi;
import com.google.gerrit.client.changes.Util;
import com.google.gerrit.client.info.ChangeInfo;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.MoveDialog;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.event.logical.shared.CloseEvent;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.PopupPanel;

class MoveAction {
  static void call(Button b, ChangeInfo info, Project.NameKey project) {
    b.setEnabled(false);
    new MoveDialog(project) {
      {
        sendButton.setText(Util.C.moveChangeSend());
      }

      @Override
      public void onSend() {
        ChangeApi.move(
            info.project(),
            info.legacyId().get(),
            getDestinationBranch(),
            getMessageText(),
            new GerritCallback<ChangeInfo>() {
              @Override
              public void onSuccess(ChangeInfo result) {
                sent = true;
                hide();
                Gerrit.display(PageLinks.toChange(project, result.legacyId()));
              }

              @Override
              public void onFailure(Throwable caught) {
                enableButtons(true);
                super.onFailure(caught);
              }
            });
      }

      @Override
      public void onClose(CloseEvent<PopupPanel> event) {
        super.onClose(event);
        b.setEnabled(true);
      }
    }.center();
  }
}
