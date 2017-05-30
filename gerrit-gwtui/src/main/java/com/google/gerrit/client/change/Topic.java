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
import com.google.gerrit.client.ui.InlineHyperlink;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.UIObject;
import com.google.gwtexpui.globalkey.client.NpTextBox;

/** Displays (and edits) the change topic string. */
class Topic extends Composite {
  interface Binder extends UiBinder<HTMLPanel, Topic> {}

  private static final Binder uiBinder = GWT.create(Binder.class);

  private PatchSet.Id psId;
  private Project.NameKey project;
  private boolean canEdit;

  @UiField Element show;
  @UiField InlineHyperlink text;
  @UiField Image editIcon;

  @UiField Element form;
  @UiField NpTextBox input;
  @UiField Button save;
  @UiField Button cancel;

  Topic() {
    initWidget(uiBinder.createAndBindUi(this));
    editIcon.addDomHandler(
        new ClickHandler() {
          @Override
          public void onClick(ClickEvent event) {
            onEdit();
          }
        },
        ClickEvent.getType());
  }

  void set(ChangeInfo info, String revision) {
    canEdit = info.hasActions() && info.actions().containsKey("topic");

    psId = new PatchSet.Id(info.legacyId(), info.revisions().get(revision)._number());
    project = info.projectNameKey();

    initTopicLink(info);
    editIcon.setVisible(canEdit);
    if (!canEdit) {
      show.setTitle(null);
    }
  }

  private void initTopicLink(ChangeInfo info) {
    if (info.topic() != null && !info.topic().isEmpty()) {
      String topic = info.topic();
      text.setText(topic);
      text.setTargetHistoryToken(PageLinks.topicQuery(info.status(), topic));
    }
  }

  boolean canEdit() {
    return canEdit;
  }

  void onEdit() {
    if (canEdit) {
      UIObject.setVisible(show, false);
      UIObject.setVisible(form, true);

      input.setText(text.getText());
      input.setFocus(true);
      input.selectAll();
    }
  }

  @UiHandler("cancel")
  void onCancel(@SuppressWarnings("unused") ClickEvent e) {
    input.setFocus(false);
    UIObject.setVisible(form, false);
    UIObject.setVisible(show, true);
  }

  @UiHandler("input")
  void onKeyDownInput(KeyDownEvent e) {
    if (e.getNativeKeyCode() == KeyCodes.KEY_ESCAPE) {
      onCancel(null);
    } else if (e.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
      e.stopPropagation();
      e.preventDefault();
      onSave(null);
    }
  }

  @UiHandler("save")
  void onSave(@SuppressWarnings("unused") ClickEvent e) {
    ChangeApi.topic(
        psId.getParentKey().get(),
        Project.NameKey.asStringOrNull(project),
        input.getValue().trim(),
        new GerritCallback<String>() {
          @Override
          public void onSuccess(String result) {
            Gerrit.display(PageLinks.toChange(psId));
          }
        });
    onCancel(null);
  }

  @UiHandler("save")
  void onSaveKeyPress(KeyPressEvent e) {
    if (e.getNativeEvent().getKeyCode() == KeyCodes.KEY_ENTER) {
      e.stopPropagation();
    }
  }
}
