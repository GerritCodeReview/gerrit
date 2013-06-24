//Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.client.diff;

import com.google.gerrit.client.account.AccountInfo;
import com.google.gerrit.client.patches.PatchUtil;
import com.google.gerrit.client.ui.CommentLinkProcessor;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.DoubleClickEvent;
import com.google.gwt.event.dom.client.DoubleClickHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwtexpui.globalkey.client.NpTextArea;

import java.sql.Timestamp;

/** An HtmlPanel for displaying and editing a draft */
//TODO: Make the buttons functional.
class DraftBox extends CommentBox {
  interface Binder extends UiBinder<HTMLPanel, DraftBox> {}
  private static Binder uiBinder = GWT.create(Binder.class);

  interface DraftBoxStyle extends CssResource {
    String edit();
    String view();
  }

  @UiField
  NpTextArea editArea;

  @UiField
  DraftBoxStyle draftStyle;

  @UiField
  Button edit;

  @UiField
  Button save;

  @UiField
  Button cancel;

  @UiField
  Button discard;

  private HandlerRegistration messageClick;

  DraftBox(AccountInfo author, Timestamp when, String message,
      CommentLinkProcessor linkProcessor) {
    initWidget(uiBinder.createAndBindUi(this));
    init(author, when, message, linkProcessor, true);
    setEdit(false);
    // TODO: Need a resize handler on editArea.
  }

  @Override
  protected void onLoad() {
    super.onLoad();

    messageClick = contentPanelMessage.addDomHandler(new DoubleClickHandler() {
      @Override
      public void onDoubleClick(DoubleClickEvent arg0) {
        editArea.setText(contentPanelMessage.getText());
        setEdit(!isEdit());
        runClickCallback();
      }
    }, DoubleClickEvent.getType());
  }

  private void setEdit(boolean edit) {
    if (edit) {
      removeStyleName(draftStyle.view());
      addStyleName(draftStyle.edit());
    } else {
      removeStyleName(draftStyle.edit());
      addStyleName(draftStyle.view());
    }
  }

  private boolean isEdit() {
    return getStyleName().contains(draftStyle.edit());
  }

  @UiHandler("edit")
  void onEdit(ClickEvent e) {
    if (!isEdit()) {
      setEdit(true);
    }
    editArea.setText(contentPanelMessage.getText());
    runClickCallback();
    editArea.setFocus(true);
  }

  @UiHandler("cancel")
  void on

  private void render() {
    final Timestamp on = comment.getWrittenOn();
    setDateText(PatchUtil.M.draftSaved(new java.util.Date(on.getTime())));
    setMessageText(comment.getMessage());
    stateEdit(false);
  }

  @Override
  public void onUnload() {
    super.onUnload();

    messageClick.removeHandler();
    messageClick = null;
  }
}
