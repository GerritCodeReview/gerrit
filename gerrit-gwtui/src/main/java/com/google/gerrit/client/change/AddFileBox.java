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

import com.google.gerrit.client.Dispatcher;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.info.ChangeInfo.RevisionInfo;
import com.google.gerrit.client.ui.RemoteSuggestBox;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.logical.shared.CloseEvent;
import com.google.gwt.event.logical.shared.CloseHandler;
import com.google.gwt.event.logical.shared.SelectionEvent;
import com.google.gwt.event.logical.shared.SelectionHandler;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.Widget;

class AddFileBox extends Composite {
  interface Binder extends UiBinder<HTMLPanel, AddFileBox> {}

  private static final Binder uiBinder = GWT.create(Binder.class);

  private final Project.NameKey project;
  private final Change.Id changeId;
  private final RevisionInfo revision;
  private final FileTable fileTable;

  @UiField Button open;
  @UiField Button cancel;

  @UiField(provided = true)
  RemoteSuggestBox path;

  AddFileBox(
      @Nullable Project.NameKey project,
      Change.Id changeId,
      RevisionInfo revision,
      FileTable files) {
    this.project = project;
    this.changeId = changeId;
    this.revision = revision;
    this.fileTable = files;

    path = new RemoteSuggestBox(new PathSuggestOracle(project, changeId, revision));
    path.addSelectionHandler(
        new SelectionHandler<String>() {
          @Override
          public void onSelection(SelectionEvent<String> event) {
            open(event.getSelectedItem());
          }
        });
    path.addCloseHandler(
        new CloseHandler<RemoteSuggestBox>() {
          @Override
          public void onClose(CloseEvent<RemoteSuggestBox> event) {
            hide();
            fileTable.registerKeys();
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

  @UiHandler("open")
  void onOpen(@SuppressWarnings("unused") ClickEvent e) {
    open(path.getText());
  }

  private void open(String path) {
    hide();
    Gerrit.display(
        Dispatcher.toEditScreen(project, new PatchSet.Id(changeId, revision._number()), path));
  }

  @UiHandler("cancel")
  void onCancel(@SuppressWarnings("unused") ClickEvent e) {
    hide();
    fileTable.registerKeys();
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
