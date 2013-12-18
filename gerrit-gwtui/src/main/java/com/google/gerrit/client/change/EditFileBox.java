//Copyright (C) 2013 The Android Open Source Project
//
//Licensed under the Apache License, Version 2.0 (the "License");
//you may not use this file except in compliance with the License.
//You may obtain a copy of the License at
//
//http://www.apache.org/licenses/LICENSE-2.0
//
//Unless required by applicable law or agreed to in writing, software
//distributed under the License is distributed on an "AS IS" BASIS,
//WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//See the License for the specific language governing permissions and
//limitations under the License.

package com.google.gerrit.client.change;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.VoidResult;
import com.google.gerrit.client.changes.ChangeFileApi;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.TextBoxChangeListener;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtexpui.globalkey.client.NpTextArea;
import com.google.gwtexpui.globalkey.client.NpTextBox;

class EditFileBox extends Composite {
  interface Binder extends UiBinder<HTMLPanel, EditFileBox> {}
  private static final Binder uiBinder = GWT.create(Binder.class);

  final private PatchSet.Id id;
  final private String fileName;
  final private String fileContent;

  @UiField NpTextBox file;
  @UiField NpTextArea content;
  @UiField Button save;
  @UiField Button reload;
  @UiField Button cancel;

  EditFileBox(
      PatchSet.Id id,
      String fileC,
      String fileName) {
    this.id = id;
    this.fileName = fileName;
    this.fileContent = fileC;
    initWidget(uiBinder.createAndBindUi(this));
    new TextBoxChangeListener(file) {
      public void onTextChanged(String newText) {
        reload.setEnabled(!file.getText().trim().isEmpty());
      }
    };
    new TextBoxChangeListener(content) {
      public void onTextChanged(String newText) {
        save.setEnabled(!file.getText().trim().isEmpty()
            && !newText.trim().equals(fileContent));
      }
    };
  }

  @Override
  protected void onLoad() {
    file.setText(fileName);
    file.setEnabled(fileName.isEmpty());
    reload.setEnabled(!fileName.isEmpty());
    content.setText(fileContent);
    save.setEnabled(false);
    Scheduler.get().scheduleDeferred(new ScheduledCommand() {
      @Override
      public void execute() {
        content.setFocus(true);
      }});
  }

  @UiHandler("save")
  void onSave(ClickEvent e) {
    ChangeFileApi.putContent(id, file.getText(), content.getText().trim(),
        new AsyncCallback<VoidResult>() {
          @Override
          public void onSuccess(VoidResult result) {
            Gerrit.display(PageLinks.toChange(id.getParentKey()));
            hide();
          }

          @Override
          public void onFailure(Throwable caught) {
          }
        });
  }

  @UiHandler("reload")
  void onRefresh(ClickEvent e) {
    ChangeFileApi.getContent(id, file.getText().trim(),
        new GerritCallback<String>() {
          @Override
          public void onSuccess(String result) {
            content.setText(result);
          }
        });
  }

  @UiHandler("cancel")
  void onCancel(ClickEvent e) {
    hide();
  }

  protected void hide() {
    for (Widget w = getParent(); w != null; w = w.getParent()) {
      if (w instanceof PopupPanel) {
        ((PopupPanel) w).hide();
        break;
      }
    }
  }
}
