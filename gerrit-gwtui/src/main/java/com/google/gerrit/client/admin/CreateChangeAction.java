// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.client.admin;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.changes.ChangeInfo;
import com.google.gerrit.client.changes.Util;
import com.google.gerrit.client.projects.ProjectApi;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.NewChangeDialog;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.user.client.ui.Button;

class CreateChangeAction {

  static void call(Button b, final Project.NameKey project) {
    b.setEnabled(false);
    new NewChangeDialog(b, project, Util.C.createNewChangeTitle(),
        Util.C.createNewChangeAction()) {
      {
        sendButton.setText(Util.C.createNewChangeAction());
      }

      @Override
      public void onSend() {
        ProjectApi.createChange(project,
            getDestinationBranch(),
            getMessageText(),
            new GerritCallback<ChangeInfo>() {
              @Override
              public void onSuccess(ChangeInfo result) {
                sent = true;
                hide();
                Gerrit.display(PageLinks.toChange(result.legacy_id()));
              }

              @Override
              public void onFailure(Throwable caught) {
                enableButtons(true);
                super.onFailure(caught);
              }
            });
      }
    }.center();
  }
}
