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

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.VoidResult;
import com.google.gerrit.client.changes.ChangeEditApi;
import com.google.gerrit.client.info.ChangeInfo.RevisionInfo;
import com.google.gerrit.client.ui.RemoteSuggestBox;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.logical.shared.CloseEvent;
import com.google.gwt.event.logical.shared.CloseHandler;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtexpui.globalkey.client.NpTextBox;

class RenameFileBox extends Composite {
  interface Binder extends UiBinder<HTMLPanel, RenameFileBox> {}

  private static final Binder uiBinder = GWT.create(Binder.class);

  private final Change.Id changeId;

  @UiField Button rename;
  @UiField Button cancel;

  @UiField(provided = true)
  RemoteSuggestBox path;

  @UiField NpTextBox newPath;

  RenameFileBox(Change.Id changeId, RevisionInfo revision) {
    this.changeId = changeId;

    path = new RemoteSuggestBox(new PathSuggestOracle(changeId, revision));
    path.addCloseHandler(
        new CloseHandler<RemoteSuggestBox>() {
          @Override
          public void onClose(CloseEvent<RemoteSuggestBox> event) {
            hide();
          }
        });

    initWidget(uiBinder.createAndBindUi(this));
  }

  void setFocus(boolean focus) {
    path.setFocus(focus);
  }

  void clearPath() {
    path.setText("");
  }

  @UiHandler("rename")
  void onRename(@SuppressWarnings("unused") ClickEvent e) {
    rename(path.getText(), newPath.getText());
  }

  private void rename(String path, String newPath) {
    hide();
    ChangeEditApi.rename(
        changeId.get(),
        path,
        newPath,
        new AsyncCallback<VoidResult>() {
          @Override
          public void onSuccess(VoidResult result) {
            Gerrit.display(PageLinks.toChangeInEditMode(changeId));
          }

          @Override
          public void onFailure(Throwable caught) {}
        });
  }

  @UiHandler("cancel")
  void onCancel(@SuppressWarnings("unused") ClickEvent e) {
    hide();
  }

  private void hide() {
    for (Widget w = getParent(); w != null; w = w.getParent()) {
      if (w instanceof PopupPanel) {
        ((PopupPanel) w).hide();
        break;
      }
    }
  }
}
