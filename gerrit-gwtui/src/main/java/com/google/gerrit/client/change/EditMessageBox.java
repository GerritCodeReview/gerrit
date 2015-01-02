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
import com.google.gerrit.client.VoidResult;
import com.google.gerrit.client.changes.ChangeEditApi;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.TextBoxChangeListener;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.reviewdb.client.Change;
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
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtexpui.globalkey.client.NpTextArea;

class EditMessageBox extends Composite {
  interface Binder extends UiBinder<HTMLPanel, EditMessageBox> {}
  private static final Binder uiBinder = GWT.create(Binder.class);

  private final Change.Id changeId;
  private String originalMessage;

  @UiField NpTextArea message;
  @UiField Button save;
  @UiField Button cancel;

  EditMessageBox(
      Change.Id changeId,
      String msg) {
    this.changeId = changeId;
    this.originalMessage = msg.trim();
    initWidget(uiBinder.createAndBindUi(this));
    message.getElement().setAttribute("wrap", "off");
    message.setText("");
    new TextBoxChangeListener(message) {
      @Override
      public void onTextChanged(String newText) {
        save.setEnabled(!newText.trim()
            .equals(originalMessage));
      }
    };
  }

  @Override
  protected void onLoad() {
    if (message.getText().isEmpty()) {
      message.setText(originalMessage);
      save.setEnabled(false);
    }
    Scheduler.get().scheduleDeferred(new ScheduledCommand() {
      @Override
      public void execute() {
        message.setFocus(true);
      }});
  }

  @UiHandler("save")
  void onSave(@SuppressWarnings("unused") ClickEvent e) {
    save.setEnabled(false);
    ChangeEditApi.putMessage(changeId.get(), message.getText().trim(),
        new GerritCallback<VoidResult>() {
          @Override
          public void onSuccess(VoidResult result) {
            Gerrit.display(PageLinks.toChangeInEditMode(changeId));
            hide();
          }
        });
  }

  @UiHandler("cancel")
  void onCancel(@SuppressWarnings("unused") ClickEvent e) {
    message.setText("");
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
