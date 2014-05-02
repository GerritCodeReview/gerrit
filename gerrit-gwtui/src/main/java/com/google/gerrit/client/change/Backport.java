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

package com.google.gerrit.client.change;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.changes.ChangeApi;
import com.google.gerrit.client.changes.ChangeInfo;
import com.google.gerrit.client.changes.ChangeInfo.MergeableInfo;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.common.PageLinks;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.ListBox;

class Backport extends Composite {

  interface Binder extends UiBinder<HTMLPanel, Backport> {}
  private static final Binder uiBinder = GWT.create(Binder.class);

  @UiField ListBox backportBranch;
  @UiField Button backport;

  private ChangeInfo changeInfo;

  Backport() {
    initWidget(uiBinder.createAndBindUi(this));
    getElement().setId("backport_actions");
  }

  void display(ChangeInfo changeInfo, MergeableInfo mergeableInfo) {
    this.changeInfo = changeInfo;
    JsArrayString mergeableInto = mergeableInfo.mergeable_into();
    for (int i = 0; i < mergeableInto.length(); i++) {
      backportBranch.addItem(mergeableInto.get(i));
    }
    backportBranch.setSelectedIndex(mergeableInto.length() - 1);
  }

  @UiHandler("backport")
  void onBackport(ClickEvent e) {
    int i = backportBranch.getSelectedIndex();
    if (0 <= i) {
      String branch = backportBranch.getValue(i);
      String current = changeInfo.current_revision();
      ChangeApi.cherrypick(changeInfo.legacy_id().get(),
          current, branch, changeInfo.revision(current).commit().message(),
          new GerritCallback<ChangeInfo>() {
            @Override
            public void onSuccess(ChangeInfo result) {
              Gerrit.display(PageLinks.toChange(result.legacy_id()));
            }
          });
    }
  }
}
