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

package com.google.gerrit.client.admin;

import com.google.gerrit.client.Dispatcher;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.changes.ChangeApi;
import com.google.gerrit.client.info.ChangeInfo;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gwt.user.client.ui.Button;

public class EditConfigAction {
  static void call(Button b, String project) {
    b.setEnabled(false);

    ChangeApi.createChange(
        project,
        RefNames.REFS_CONFIG,
        null,
        AdminConstants.I.editConfigMessage(),
        null,
        new GerritCallback<ChangeInfo>() {
          @Override
          public void onSuccess(ChangeInfo result) {
            Gerrit.display(
                Dispatcher.toEditScreen(new PatchSet.Id(result.legacyId(), 1), "project.config"));
          }

          @Override
          public void onFailure(Throwable caught) {
            b.setEnabled(true);
            super.onFailure(caught);
          }
        });
  }
}
