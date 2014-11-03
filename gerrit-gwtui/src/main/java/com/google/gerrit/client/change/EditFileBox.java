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
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.TextBoxBase;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtexpui.globalkey.client.NpTextArea;

class EditFileBox extends Composite {
  interface Binder extends UiBinder<HTMLPanel, EditFileBox> {}
  private static final Binder uiBinder = GWT.create(Binder.class);

  private final PatchSet.Id id;
  private final String fileName;
  private final String fileContent;

  @UiField FileTextBox file;
  @UiField NpTextArea content;
  @UiField Button save;
  @UiField Button cancel;

  EditFileBox(
      PatchSet.Id id,
      String fileC,
      String fileName) {
    this.id = id;
    this.fileName = fileName;
    this.fileContent = fileC;
    initWidget(uiBinder.createAndBindUi(this));
    new EditFileBoxListener(content);
    new EditFileBoxListener(file);
  }

  @Override
  protected void onLoad() {
    file.set(id, content);
    file.setText(fileName);
    file.setEnabled(fileName.isEmpty());
    content.setText(fileContent);
    save.setEnabled(false);
    Scheduler.get().scheduleDeferred(new ScheduledCommand() {
      @Override
      public void execute() {
        if (fileName.isEmpty()) {
          file.setFocus(true);
        } else {
          content.setFocus(true);
        }
      }});
  }

  @UiHandler("save")
  void onSave(@SuppressWarnings("unused") ClickEvent e) {
    ChangeFileApi.putContent(id, file.getText(), content.getText(),
        new GerritCallback<VoidResult>() {
          @Override
          public void onSuccess(VoidResult result) {
            Gerrit.display(PageLinks.toChangeInEditMode(id.getParentKey()));
            hide();
          }
        });
  }

  @UiHandler("cancel")
  void onCancel(@SuppressWarnings("unused") ClickEvent e) {
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

  private class EditFileBoxListener extends TextBoxChangeListener {
    public EditFileBoxListener(TextBoxBase base) {
      super(base);
    }

    @Override
    public void onTextChanged(String newText) {
      save.setEnabled(!file.getText().trim().isEmpty()
          && !newText.trim().equals(fileContent));
    }
  }
}
