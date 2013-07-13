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
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.MouseMoveEvent;
import com.google.gwt.event.dom.client.MouseMoveHandler;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwtexpui.globalkey.client.NpTextArea;

import net.codemirror.lib.CodeMirror;

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

  private static final int INITIAL_COLS = 60;
  private static final int INITIAL_LINES = 5;
  private static final int MAX_LINES = 30;

  private boolean isNew;
  private PublishedBox replyToBox;
  private Timer expandTimer;

  DraftBox(
      CodeMirrorDemo host,
      CodeMirror cm,
      PatchSet.Id id,
      CommentInfo info,
      CommentLinkProcessor linkProcessor,
      boolean isNew,
      boolean saveOnInit) {
    super(host, cm, uiBinder, id, info, linkProcessor, true);

    this.isNew = isNew;
    editArea.setText(info.message());
    editArea.setCharacterWidth(INITIAL_COLS);
    editArea.setVisibleLines(INITIAL_LINES);
    editArea.setSpellCheck(true);
    if (saveOnInit) {
      onSave(null);
    }
    if (isNew) {
      addStyleName(draftStyle.newDraft());
    }
    expandTimer = new Timer() {
      @Override
      public void run() {
        expandText();
      }
    };
    addDomHandler(new MouseMoveHandler() {
      @Override
      public void onMouseMove(MouseMoveEvent event) {
        resizePaddingWidget();
      }
    }, MouseMoveEvent.getType());
  }

  private void expandText() {
    double cols = editArea.getCharacterWidth();
    int rows = 2;
    for (String line : editArea.getText().split("\n")) {
      rows += Math.ceil((1.0 + line.length()) / cols);
    }
    rows = Math.max(INITIAL_LINES, Math.min(rows, MAX_LINES));
    if (editArea.getVisibleLines() != rows) {
      editArea.setVisibleLines(rows);
    }
    resizePaddingWidget();
  }

  void setEdit(boolean edit) {
    if (edit) {
      setOpen(true);
      removeStyleName(draftStyle.view());
      addStyleName(draftStyle.edit());
      editArea.setText(getOriginal().message());
      expandText();
      editArea.setFocus(true);
    } else {
      expandTimer.cancel();
      removeStyleName(draftStyle.edit());
      addStyleName(draftStyle.view());
    }
    resizePaddingWidget();
  }

  void registerReplyToBox(PublishedBox box) {
    replyToBox = box;
  }

  private void removeUI() {
    expandTimer.cancel();
    if (replyToBox != null) {
      replyToBox.unregisterReplyBox();
    }
    CommentInfo info = getOriginal();
    getDiffView().removeDraft(info.side(), info.line() - 1);
    removeFromParent();
    getSelfWidget().clear();
    PaddingManager manager = getPaddingManager();
    manager.remove(this);
    manager.resizePaddingWidget();
    getCm().focus();
  }

  @UiHandler("contentPanelMessage")
  void onDoubleClick(DoubleClickEvent e) {
    setEdit(true);
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
        setMessageText(message);
        setDate(result.updated());
        setEdit(false);
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
    getCm().focus();
  }

  @UiHandler("cancel")
  void onCancel(ClickEvent e) {
    setEdit(false);
    getCm().focus();
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

  @UiHandler("editArea")
  void onCtrlS(KeyDownEvent e) {
    if ((e.isControlKeyDown() || e.isMetaKeyDown())
        && !e.isAltKeyDown() && !e.isShiftKeyDown()) {
      switch (e.getNativeKeyCode()) {
        case 's':
        case 'S':
          e.preventDefault();
          onSave(null);
          return;
      }
    }
    expandTimer.schedule(250);
  }

  /** TODO: Unused now. Re-enable this after implementing auto-save */
  void onEsc(KeyDownEvent e) {
    if (e.getNativeKeyCode() == KeyCodes.KEY_ESCAPE) {
      if (isNew) {
        removeUI();
      } else {
        onCancel(null);
      }
      e.preventDefault();
    }
  }
}
