// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.client.patches;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.CommentPanel;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.DoubleClickEvent;
import com.google.gwt.event.dom.client.DoubleClickHandler;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.user.client.Timer;
import com.google.gwtjsonrpc.common.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtexpui.globalkey.client.NpTextArea;
import com.google.gwtjsonrpc.common.VoidResult;

import java.sql.Timestamp;

public class CommentEditorPanel extends CommentPanel implements ClickHandler,
    DoubleClickHandler {
  private static final int INITIAL_COLS = 60;
  private static final int INITIAL_LINES = 5;
  private static final int MAX_LINES = 30;
  private static final AsyncCallback<VoidResult> NULL_CALLBACK =
      new AsyncCallback<VoidResult>() {
        @Override
        public void onFailure(Throwable caught) {
        }

        @Override
        public void onSuccess(VoidResult result) {
        }
      };

  private PatchLineComment comment;

  private final NpTextArea text;
  private final Button edit;
  private final Button save;
  private final Button cancel;
  private final Button discard;
  private final Timer expandTimer;

  public CommentEditorPanel(final PatchLineComment plc) {
    comment = plc;

    addStyleName(Gerrit.RESOURCES.css().commentEditorPanel());
    setAuthorNameText(PatchUtil.C.draft());
    setMessageText(plc.getMessage());
    addDoubleClickHandler(this);

    expandTimer = new Timer() {
      @Override
      public void run() {
        expandText();
      }
    };
    text = new NpTextArea();
    text.setText(comment.getMessage());
    text.setCharacterWidth(INITIAL_COLS);
    text.setVisibleLines(INITIAL_LINES);
    text.setSpellCheck(true);
    text.addKeyDownHandler(new KeyDownHandler() {
      @Override
      public void onKeyDown(final KeyDownEvent event) {
        if ((event.isControlKeyDown() || event.isMetaKeyDown())
            && !event.isAltKeyDown() && !event.isShiftKeyDown()) {
          switch (event.getNativeKeyCode()) {
            case 's':
            case 'S':
              event.preventDefault();
              onSave(NULL_CALLBACK);
              return;
          }
        }

        expandTimer.schedule(250);
      }
    });
    addContent(text);

    edit = new Button();
    edit.setText(PatchUtil.C.buttonEdit());
    edit.addClickHandler(this);
    addButton(edit);

    save = new Button();
    save.setText(PatchUtil.C.buttonSave());
    save.addClickHandler(this);
    addButton(save);

    cancel = new Button();
    cancel.setText(PatchUtil.C.buttonCancel());
    cancel.addClickHandler(this);
    addButton(cancel);

    discard = new Button();
    discard.setText(PatchUtil.C.buttonDiscard());
    discard.addClickHandler(this);
    addButton(discard);

    setOpen(true);
    if (isNew()) {
      edit();
    } else {
      render();
    }
  }

  private void expandText() {
    final double cols = text.getCharacterWidth();
    int rows = 2;
    for (final String line : text.getText().split("\n")) {
      rows += Math.ceil((1.0 + line.length()) / cols);
    }
    rows = Math.max(INITIAL_LINES, Math.min(rows, MAX_LINES));
    if (text.getVisibleLines() != rows) {
      text.setVisibleLines(rows);
    }
  }

  private void edit() {
    if (!isOpen()) {
      setOpen(true);
    }
    text.setText(comment.getMessage());
    expandText();
    stateEdit(true);
    text.setFocus(true);
  }

  private void render() {
    final Timestamp on = comment.getWrittenOn();
    setDateText(PatchUtil.M.draftSaved(new java.util.Date(on.getTime())));
    setMessageText(comment.getMessage());
    stateEdit(false);
  }

  private void stateEdit(final boolean inEdit) {
    expandTimer.cancel();
    setMessageTextVisible(!inEdit);
    edit.setVisible(!inEdit);

    if (inEdit) {
      text.setVisible(true);
    } else {
      text.setFocus(false);
      text.setVisible(false);
    }

    save.setVisible(inEdit);
    cancel.setVisible(inEdit && !isNew());
    discard.setVisible(inEdit);
  }

  void setFocus(final boolean take) {
    if (take && !isOpen()) {
      setOpen(true);
    }
    if (text.isVisible()) {
      text.setFocus(take);
    } else if (take) {
      edit();
    }
  }

  boolean isNew() {
    return comment.getKey().get() == null;
  }

  public PatchLineComment getComment() {
    return comment;
  }

  @Override
  public void onDoubleClick(final DoubleClickEvent event) {
    edit();
  }

  @Override
  public void onClick(final ClickEvent event) {
    final Widget sender = (Widget) event.getSource();
    if (sender == edit) {
      edit();

    } else if (sender == save) {
      onSave(NULL_CALLBACK);

    } else if (sender == cancel) {
      render();

    } else if (sender == discard) {
      onDiscard();
    }
  }

  public void saveDraft(AsyncCallback<VoidResult> onSave) {
    if (isOpen() && text.isVisible()) {
      onSave(onSave);
    } else {
      onSave.onSuccess(VoidResult.INSTANCE);
    }
  }

  private void onSave(final AsyncCallback<VoidResult> onSave) {
    expandTimer.cancel();
    final String txt = text.getText().trim();
    if ("".equals(txt)) {
      return;
    }

    comment.setMessage(txt);
    text.setFocus(false);
    text.setReadOnly(true);
    save.setEnabled(false);
    cancel.setEnabled(false);
    discard.setEnabled(false);

    PatchUtil.DETAIL_SVC.saveDraft(comment,
        new GerritCallback<PatchLineComment>() {
          public void onSuccess(final PatchLineComment result) {
            notifyDraftDelta(isNew() ? 1 : 0);
            comment = result;
            text.setReadOnly(false);
            save.setEnabled(true);
            cancel.setEnabled(true);
            discard.setEnabled(true);
            render();
            onSave.onSuccess(VoidResult.INSTANCE);
          }

          @Override
          public void onFailure(final Throwable caught) {
            text.setReadOnly(false);
            text.setFocus(true);
            save.setEnabled(true);
            cancel.setEnabled(true);
            discard.setEnabled(true);
            super.onFailure(caught);
            onSave.onFailure(caught);
          }
        });
  }

  private void notifyDraftDelta(final int delta) {
    CommentEditorContainer c = getContainer();
    if (c != null) {
      c.notifyDraftDelta(delta);
    }
  }

  private void onDiscard() {
    expandTimer.cancel();
    if (isNew()) {
      text.setFocus(false);
      removeUI();
      return;
    }

    text.setFocus(false);
    text.setReadOnly(true);
    save.setEnabled(false);
    cancel.setEnabled(false);
    discard.setEnabled(false);

    PatchUtil.DETAIL_SVC.deleteDraft(comment.getKey(),
        new GerritCallback<VoidResult>() {
          public void onSuccess(final VoidResult result) {
            notifyDraftDelta(-1);
            removeUI();
          }

          @Override
          public void onFailure(final Throwable caught) {
            text.setReadOnly(false);
            text.setFocus(true);
            save.setEnabled(true);
            cancel.setEnabled(true);
            discard.setEnabled(true);
            super.onFailure(caught);
          }
        });
  }

  private void removeUI() {
    CommentEditorContainer c = getContainer();
    if (c != null) {
      c.remove(this);
    }
  }

  private CommentEditorContainer getContainer() {
    Widget p = getParent();
    while (p != null) {
      if (p instanceof CommentEditorContainer) {
        return (CommentEditorContainer) p;
      }
      p = p.getParent();
    }
    return null;
  }
}
