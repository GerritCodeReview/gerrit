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
import com.google.gerrit.client.changes.ChangeInfo;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.RebaseDialog;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gwt.event.logical.shared.CloseEvent;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.PopupPanel;

class RebaseAction {
  static void call(final Button b, final String project, final String branch,
      final Change.Id id, final String revision, final boolean enabled) {
    b.setEnabled(false);

    new RebaseDialog(project, branch, id, enabled) {
      @Override
      public void onSend() {
        ChangeApi.rebase(id.get(), revision, getBase(), new GerritCallback<ChangeInfo>() {
          @Override
          public void onSuccess(ChangeInfo result) {
            sent = true;
            hide();
            Gerrit.display(PageLinks.toChange(id));
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
