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
import com.google.gerrit.client.changes.ChangeInfo;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.UIObject;
import com.google.gwtexpui.globalkey.client.NpTextArea;
import com.google.gwtexpui.globalkey.client.NpTextBox;

/** Displays (and edits) the change topic string. */
class Topic extends Composite {
  interface Binder extends UiBinder<HTMLPanel, Topic> {}
  private static final Binder uiBinder = GWT.create(Binder.class);

  private PatchSet.Id psId;
  private boolean canEdit;

  @UiField FlowPanel show;
  @UiField InlineLabel text;
  @UiField Image editIcon;

  @UiField Element form;
  @UiField NpTextBox input;
  @UiField NpTextArea message;
  @UiField Button save;
  @UiField Button cancel;

  Topic() {
    initWidget(uiBinder.createAndBindUi(this));
    show.addDomHandler(
      new ClickHandler() {
        @Override
        public void onClick(ClickEvent event) {
          onEdit();
        }
      },
      ClickEvent.getType());
  }

  void set(ChangeInfo info, String revision) {
    canEdit = info.has_actions()
        && info.actions().containsKey("topic")
        && info.actions().get("topic").enabled();

    psId = new PatchSet.Id(
        info.legacy_id(),
        info.revisions().get(revision)._number());

    text.setText(info.topic());
    editIcon.setVisible(canEdit);
    if (!canEdit) {
      show.setTitle(null);
    }
  }

  boolean canEdit() {
    return canEdit;
  }

  void onEdit() {
    if (canEdit) {
      show.setVisible(false);
      UIObject.setVisible(form, true);

      input.setText(text.getText());
      input.setFocus(true);
    }
  }

  @UiHandler("cancel")
  void onCancel(ClickEvent e) {
    input.setFocus(false);
    show.setVisible(true);
    UIObject.setVisible(form, false);
  }

  @UiHandler("input")
  void onKeyDownInput(KeyDownEvent e) {
    if (e.getNativeKeyCode() == KeyCodes.KEY_ESCAPE) {
      onCancel(null);
    } else if (e.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
      e.stopPropagation();
      onSave(null);
    }
  }

  @UiHandler("message")
  void onKeyDownMessage(KeyDownEvent e) {
    if (e.getNativeKeyCode() == KeyCodes.KEY_ESCAPE) {
      onCancel(null);
    } else if (e.getNativeKeyCode() == KeyCodes.KEY_ENTER
        && e.isControlKeyDown()) {
      onSave(null);
    }
  }

  @UiHandler("save")
  void onSave(ClickEvent e) {
    ChangeApi.topic(
        psId.getParentKey().get(),
        input.getValue().trim(),
        message.getValue().trim(),
        new GerritCallback<String>() {
          @Override
          public void onSuccess(String result) {
            Gerrit.display(PageLinks.toChange(
                psId.getParentKey(),
                String.valueOf(psId.get())));
          }
        });
    onCancel(null);
  }
}
