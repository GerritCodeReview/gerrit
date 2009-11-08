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

import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.CommentPanel;
import com.google.gerrit.reviewdb.PatchLineComment;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.DoubleClickEvent;
import com.google.gwt.event.dom.client.DoubleClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.Focusable;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtexpui.globalkey.client.NpTextArea;
import com.google.gwtjsonrpc.client.VoidResult;

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

    addStyleName("gerrit-CommentEditorPanel");
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
    DOM.setElementPropertyBoolean(text.getElement(), "spellcheck", true);
    text.addKeyPressHandler(new KeyPressHandler() {
      @Override
      public void onKeyPress(final KeyPressEvent event) {
        if (event.getCharCode() == KeyCodes.KEY_ESCAPE
            && !event.isAnyModifierKeyDown()) {
          event.preventDefault();
          if (isNew()) {
            onDiscard();
          } else {
            render();
          }
          return;
        }

        if ((event.isControlKeyDown() || event.isMetaKeyDown())
            && !event.isAltKeyDown() && !event.isShiftKeyDown()) {
          switch (event.getCharCode()) {
            case 's':
              event.preventDefault();
              onSave(NULL_CALLBACK);
              return;

            case 'd':
              event.preventDefault();
              if (isNew()) {
                onDiscard();
              } else if (Window.confirm(PatchUtil.C.confirmDiscard())) {
                onDiscard();
              } else {
                text.setFocus(true);
              }
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
    getButtonPanel().add(edit);

    save = new Button();
    save.setText(PatchUtil.C.buttonSave());
    save.addClickHandler(this);
    getButtonPanel().add(save);

    cancel = new Button();
    cancel.setText(PatchUtil.C.buttonCancel());
    cancel.addClickHandler(this);
    getButtonPanel().add(cancel);

    discard = new Button();
    discard.setText(PatchUtil.C.buttonDiscard());
    discard.addClickHandler(this);
    getButtonPanel().add(discard);

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

    text.setVisible(inEdit);
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
    text.setReadOnly(true);
    save.setEnabled(false);
    cancel.setEnabled(false);
    discard.setEnabled(false);

    PatchUtil.DETAIL_SVC.saveDraft(comment,
        new GerritCallback<PatchLineComment>() {
          public void onSuccess(final PatchLineComment result) {
            if (isNew()) {
              notifyDraftDelta(1);
            }
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
            save.setEnabled(true);
            cancel.setEnabled(true);
            discard.setEnabled(true);
            super.onFailure(caught);
            onSave.onFailure(caught);
          }
        });
  }

  private void notifyDraftDelta(final int delta) {
    Widget p = getParent();
    if (p != null) {
      p = p.getParent();
      if (p != null) {
        ((AbstractPatchContentTable) p).notifyDraftDelta(delta);
      }
    }
  }

  private void onDiscard() {
    expandTimer.cancel();
    if (isNew()) {
      removeUI();
      return;
    }

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
            save.setEnabled(true);
            cancel.setEnabled(true);
            discard.setEnabled(true);
            super.onFailure(caught);
          }
        });
  }

  private void removeUI() {
    final FlexTable table = (FlexTable) getParent();
    final int nRows = table.getRowCount();
    for (int row = 0; row < nRows; row++) {
      final int nCells = table.getCellCount(row);
      for (int cell = 0; cell < nCells; cell++) {
        if (table.getWidget(row, cell) == this) {
          AbstractPatchContentTable.destroyEditor(table, row, cell);
          Widget p = table;
          while (p != null) {
            if (p instanceof Focusable) {
              ((Focusable) p).setFocus(true);
              break;
            }
            p = p.getParent();
          }
          return;
        }
      }
    }
  }
}
