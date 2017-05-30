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
import com.google.gerrit.client.changes.ChangeApi;
import com.google.gerrit.client.changes.Util;
import com.google.gerrit.client.info.ChangeInfo;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.TextAreaActionDialog;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.event.logical.shared.CloseEvent;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.PopupPanel;

class RevertAction {
  static void call(
      final Button b,
      final Change.Id id,
      @Nullable final Project.NameKey project,
      final String revision,
      final String commitSubject) {
    // TODO Replace ActionDialog with a nicer looking display.
    b.setEnabled(false);
    new TextAreaActionDialog(Util.C.revertChangeTitle(), Util.C.headingRevertMessage()) {
      {
        sendButton.setText(Util.C.buttonRevertChangeSend());
        message.setText(Util.M.revertChangeDefaultMessage(commitSubject, revision));
      }

      @Override
      public void onSend() {
        ChangeApi.revert(
            Project.NameKey.asStringOrNull(project),
            id.get(),
            getMessageText(),
            new GerritCallback<ChangeInfo>() {
              @Override
              public void onSuccess(ChangeInfo result) {
                sent = true;
                hide();
                Gerrit.display(PageLinks.toChange(result.projectNameKey(), result.legacyId()));
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
