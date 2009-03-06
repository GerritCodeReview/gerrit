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

import com.google.gerrit.client.reviewdb.PatchLineComment;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.TextSaveButtonListener;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.ClickListener;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HasFocus;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.KeyboardListenerAdapter;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtjsonrpc.client.VoidResult;

import java.sql.Timestamp;

class CommentEditorPanel extends Composite implements ClickListener {
  private PatchLineComment comment;
  private final TextArea text;
  private final Button save;
  private final Button discard;
  private final Label savedAt;

  CommentEditorPanel(final PatchLineComment plc) {
    comment = plc;

    final FlowPanel body = new FlowPanel();
    body.setStyleName("gerrit-CommentEditor");

    text = new TextArea();
    text.setText(comment.getMessage());
    text.setCharacterWidth(60);
    text.setVisibleLines(5);
    DOM.setElementPropertyBoolean(text.getElement(), "spellcheck", true);
    text.addKeyboardListener(new KeyboardListenerAdapter() {
      @Override
      public void onKeyPress(final Widget sender, final char kc, final int mod) {
        DOM.eventCancelBubble(DOM.eventGetCurrentEvent(), true);

        if (kc == KEY_ESCAPE && mod == 0 && isNew()) {
          DOM.eventPreventDefault(DOM.eventGetCurrentEvent());
          onDiscard();
          return;
        }

        if ((mod & MODIFIER_CTRL) == MODIFIER_CTRL) {
          switch (kc) {
            case 's':
              DOM.eventPreventDefault(DOM.eventGetCurrentEvent());
              onSave();
              return;

            case 'd':
            case KEY_BACKSPACE:
            case KEY_DELETE:
              DOM.eventPreventDefault(DOM.eventGetCurrentEvent());
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
      }
    });
    body.add(text);

    final FlowPanel buttons = new FlowPanel();
    buttons.setStyleName("gerrit-CommentEditor-Buttons");
    body.add(buttons);

    save = new Button();
    save.setText(PatchUtil.C.buttonSave());
    save.addClickListener(this);
    new TextSaveButtonListener(text, save);
    save.setEnabled(false);
    buttons.add(save);

    discard = new Button();
    discard.setText(PatchUtil.C.buttonDiscard());
    discard.addClickListener(this);
    buttons.add(discard);

    savedAt = new InlineLabel();
    if (!isNew()) {
      updateSavedAt();
    }
    buttons.add(savedAt);

    initWidget(body);
  }

  private void updateSavedAt() {
    final Timestamp on = comment.getWrittenOn();
    savedAt.setText(PatchUtil.M.draftSaved(new java.util.Date(on.getTime())));
  }

  void setFocus(final boolean take) {
    text.setFocus(take);
  }

  boolean isNew() {
    return comment.getKey().get() == null;
  }

  public void onClick(Widget sender) {
    if (sender == save) {
      onSave();
    } else if (sender == discard) {
      onDiscard();
    }
  }

  private void onSave() {
    final String txt = text.getText().trim();
    if ("".equals(txt)) {
      return;
    }

    comment.setMessage(txt);
    text.setReadOnly(true);
    save.setEnabled(false);
    discard.setEnabled(false);

    PatchUtil.DETAIL_SVC.saveDraft(comment,
        new GerritCallback<PatchLineComment>() {
          public void onSuccess(final PatchLineComment result) {
            comment = result;
            text.setReadOnly(false);
            discard.setEnabled(true);
            updateSavedAt();
          }

          @Override
          public void onFailure(final Throwable caught) {
            text.setReadOnly(false);
            save.setEnabled(true);
            discard.setEnabled(true);
            super.onFailure(caught);
          }
        });
  }

  private void onDiscard() {
    if (isNew()) {
      removeUI();
      return;
    }

    final boolean saveOn = save.isEnabled();
    text.setReadOnly(true);
    save.setEnabled(false);
    discard.setEnabled(false);

    PatchUtil.DETAIL_SVC.deleteDraft(comment.getKey(),
        new GerritCallback<VoidResult>() {
          public void onSuccess(final VoidResult result) {
            removeUI();
          }

          @Override
          public void onFailure(final Throwable caught) {
            text.setReadOnly(false);
            save.setEnabled(saveOn);
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
            if (p instanceof HasFocus) {
              ((HasFocus) p).setFocus(true);
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
