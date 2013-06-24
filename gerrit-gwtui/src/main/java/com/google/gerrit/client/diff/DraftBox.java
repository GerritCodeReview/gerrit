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

import com.google.gerrit.client.FormatUtil;
import com.google.gerrit.client.changes.CommentApi;
import com.google.gerrit.client.changes.CommentInfo;
import com.google.gerrit.client.changes.CommentInput;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.CommentLinkProcessor;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.DoubleClickEvent;
import com.google.gwt.event.dom.client.DoubleClickHandler;
import com.google.gwt.event.dom.client.MouseMoveEvent;
import com.google.gwt.event.dom.client.MouseMoveHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwtexpui.globalkey.client.NpTextArea;

/** An HtmlPanel for displaying and editing a draft */
class DraftBox extends CommentBox {
  interface Binder extends UiBinder<HTMLPanel, DraftBox> {}
  private static UiBinder<HTMLPanel, CommentBox> uiBinder =
      GWT.create(Binder.class);

  interface DraftBoxStyle extends CssResource {
    String edit();
    String view();
    String newDraft();
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
  private boolean isNew;
  private PublishedBox replyToBox;

  DraftBox(
      CodeMirrorDemo host,
      PatchSet.Id id,
      CommentInfo info,
      CommentLinkProcessor linkProcessor,
      boolean isNew,
      boolean saveOnInit) {
    super(host, uiBinder, id, info, linkProcessor, true);

    this.isNew = isNew;
    editArea.setText(contentPanelMessage.getText());
    setEdit(isNew && !saveOnInit);
    setOpen(isNew && !saveOnInit);
    if (saveOnInit) {
      onSave(null);
    }
    if (isNew) {
      addStyleName(draftStyle.newDraft());
    }
  }

  @Override
  protected void onLoad() {
    super.onLoad();

    messageClick = contentPanelMessage.addDoubleClickHandler(
        new DoubleClickHandler() {
      @Override
      public void onDoubleClick(DoubleClickEvent arg0) {
        setEdit(true);
      }
    });
    addDomHandler(new MouseMoveHandler() {
      @Override
      public void onMouseMove(MouseMoveEvent arg0) {
        resizePaddingWidget();
      }
    }, MouseMoveEvent.getType());
  }

  @Override
  protected void onUnload() {
    super.onUnload();

    messageClick.removeHandler();
    messageClick = null;
  }

  void setEdit(boolean edit) {
    if (edit) {
      removeStyleName(draftStyle.view());
      addStyleName(draftStyle.edit());
      editArea.setText(contentPanelMessage.getText());
      editArea.setFocus(true);
    } else {
      removeStyleName(draftStyle.edit());
      addStyleName(draftStyle.view());
    }
    resizePaddingWidget();
  }

  void registerReplyToBox(PublishedBox box) {
    replyToBox = box;
  }

  private void removeUI() {
    if (replyToBox != null) {
      replyToBox.unregisterReplyBox();
    }
    getPaddingWidget().clear();
    removeFromParent();
    getSelfWidget().clear();
  }

  @UiHandler("edit")
  void onEdit(ClickEvent e) {
    setEdit(true);
  }

  @UiHandler("save")
  void onSave(ClickEvent e) {
    final String message = editArea.getText();
    if (message.equals("")) {
      return;
    }
    CommentInfo original = getOriginal();
    CommentInput input = CommentInput.create(original);
    input.setMessage(message);
    GerritCallback<CommentInfo> cb = new GerritCallback<CommentInfo>() {
      @Override
      public void onSuccess(CommentInfo result) {
        updateOriginal(result);
        setEdit(false);
        setMessageText(message);
        setDateText(FormatUtil.shortFormatDayTime(result.updated()));
        resizePaddingWidget();
        if (isNew) {
          removeStyleName(draftStyle.newDraft());
          isNew = false;
        }
      }
    };
    if (isNew) {
      CommentApi.createDraft(getPatchSetId(), input, cb);
    } else {
      CommentApi.updateDraft(getPatchSetId(), original.id(), input, cb);
    }
  }

  @UiHandler("cancel")
  void onCancel(ClickEvent e) {
    setEdit(false);
    resizePaddingWidget();
  }

  @UiHandler("discard")
  void onDiscard(ClickEvent e) {
    if (isNew) {
      removeUI();
    } else {
      CommentApi.deleteDraft(getPatchSetId(), getOriginal().id(),
          new GerritCallback<JavaScriptObject>() {
        @Override
        public void onSuccess(JavaScriptObject result) {
          removeUI();
        }
      });
    }
  }
}
