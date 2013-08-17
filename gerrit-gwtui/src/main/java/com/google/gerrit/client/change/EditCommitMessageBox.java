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
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.TextBoxChangeListener;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtexpui.globalkey.client.NpTextArea;

class EditCommitMessageBox extends Composite {
  interface Binder extends UiBinder<HTMLPanel, EditCommitMessageBox> {}
  private static Binder uiBinder = GWT.create(Binder.class);

  private final Change.Id changeId;
  private final String revision;

  @UiField NpTextArea message;
  @UiField Button submit;
  @UiField Button cancel;
  private String originalMessage;

  EditCommitMessageBox(
      Change.Id changeId,
      String revision,
      String msg) {
    this.changeId = changeId;
    this.revision = revision;
    this.originalMessage = msg.trim();
    initWidget(uiBinder.createAndBindUi(this));
    new TextBoxChangeListener(message) {
      public void onTextChanged(String newText) {
        submit.setEnabled(!newText.trim()
            .equals(originalMessage));
      }
    };
  }

  @Override
  protected void onLoad() {
    Scheduler.get().scheduleDeferred(new ScheduledCommand() {
      @Override
      public void execute() {
        message.setFocus(true);
        message.setText(originalMessage);
        message.setCursorPos(0);
        submit.setEnabled(false);
      }});
  }

  @UiHandler("submit")
  void onSubmit(ClickEvent e) {
    ChangeApi.edit(changeId.get(), revision, message.getText().trim(),
        new GerritCallback<JavaScriptObject>() {
          @Override
          public void onSuccess(JavaScriptObject msg) {
            Gerrit.display(PageLinks.toChange2(changeId));
            hide();
          };
        });
  }

  @UiHandler("cancel")
  void onCancel(ClickEvent e) {
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
