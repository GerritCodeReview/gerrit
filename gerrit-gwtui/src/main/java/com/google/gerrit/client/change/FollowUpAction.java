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
import com.google.gerrit.client.info.ChangeInfo;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.common.PageLinks;
import com.google.gwt.user.client.ui.Button;

class FollowUpAction extends ActionMessageBox {
  private final String project;
  private final String branch;
  private final String topic;
  private final String base;

  FollowUpAction(Button b, String project, String branch, String topic, String key) {
    super(b);
    this.project = project;
    this.branch = branch;
    this.topic = topic;
    this.base = project + "~" + branch + "~" + key;
  }

  @Override
  void send(String message) {
    ChangeApi.createChange(
        project,
        branch,
        topic,
        message,
        base,
        new GerritCallback<ChangeInfo>() {
          @Override
          public void onSuccess(ChangeInfo result) {
            Gerrit.display(PageLinks.toChange(result.projectNameKey(), result.legacyId()));
            hide();
          }
        });
  }
}
