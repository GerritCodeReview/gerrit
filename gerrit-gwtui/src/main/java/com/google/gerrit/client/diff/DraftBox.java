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
import com.google.gerrit.common.changes.Side;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.RepeatingCommand;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.DoubleClickEvent;
import com.google.gwt.event.dom.client.DoubleClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.MouseMoveEvent;
import com.google.gwt.event.dom.client.MouseMoveHandler;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.UIObject;
import com.google.gwtexpui.globalkey.client.NpTextArea;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;

import net.codemirror.lib.CodeMirror;

/** An HtmlPanel for displaying and editing a draft */
class DraftBox extends CommentBox {
  interface Binder extends UiBinder<HTMLPanel, DraftBox> {}
  private static Binder uiBinder = GWT.create(Binder.class);

  private static final int INITIAL_LINES = 5;
  private static final int MAX_LINES = 30;

  private final SideBySide2 parent;
  private final CodeMirror cm;
  private final CommentLinkProcessor linkProcessor;
  private final PatchSet.Id psId;
  private CommentInfo comment;
  private PublishedBox replyToBox;
  private Timer expandTimer;

  @UiField Element summary;
  @UiField Element date;

  @UiField Element p_view;
  @UiField HTML message;
  @UiField Button edit;
  @UiField Button discard1;

  @UiField Element p_edit;
  @UiField NpTextArea editArea;
  @UiField Button save;
  @UiField Button cancel;
  @UiField Button discard2;

  DraftBox(
      SideBySide2 parent,
      CodeMirror cm,
      CommentLinkProcessor clp,
      PatchSet.Id id,
      CommentInfo info) {
    this.parent = parent;
    this.cm = cm;
    this.linkProcessor = clp;
    this.psId = id;
    initWidget(uiBinder.createAndBindUi(this));

    expandTimer = new Timer() {
      @Override
      public void run() {
        expandText();
      }
    };
    set(info);

    addDomHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        if (!isEdit()) {
          setOpen(!isOpen());
        }
      }
    }, ClickEvent.getType());
    addDomHandler(new DoubleClickHandler() {
      @Override
      public void onDoubleClick(DoubleClickEvent event) {
        if (isEdit()) {
          editArea.setFocus(true);
        } else {
          setOpen(true);
          setEdit(true);
        }
      }
    }, DoubleClickEvent.getType());
    addDomHandler(new MouseMoveHandler() {
      @Override
      public void onMouseMove(MouseMoveEvent event) {
        resizePaddingWidget();
      }
    }, MouseMoveEvent.getType());
  }

  private void set(CommentInfo info) {
    date.setInnerText(FormatUtil.shortFormatDayTime(info.updated()));
    if (info.message() != null) {
      String msg = info.message().trim();
      summary.setInnerText(msg);
      message.setHTML(linkProcessor.apply(
          new SafeHtmlBuilder().append(msg).wikify()));
    }
    this.comment = info;
  }

  @Override
  CommentInfo getCommentInfo() {
    return comment;
  }

  @Override
  boolean isOpen() {
    return UIObject.isVisible(p_view);
  }

  @Override
  void setOpen(boolean open) {
    UIObject.setVisible(summary, !open);
    UIObject.setVisible(p_view, open);
    super.setOpen(open);
  }

  private void expandText() {
    double cols = editArea.getCharacterWidth();
    int rows = 2;
    for (String line : editArea.getValue().split("\n")) {
      rows += Math.ceil((1.0 + line.length()) / cols);
    }
    rows = Math.max(INITIAL_LINES, Math.min(rows, MAX_LINES));
    if (editArea.getVisibleLines() != rows) {
      editArea.setVisibleLines(rows);
    }
    resizePaddingWidget();
  }

  private boolean isEdit() {
    return UIObject.isVisible(p_edit);
  }

  void setEdit(boolean edit) {
    UIObject.setVisible(summary, false);
    UIObject.setVisible(p_view, !edit);
    UIObject.setVisible(p_edit, edit);

    if (edit) {
      final String msg = comment.message() != null
          ? comment.message().trim()
          : "";
      editArea.setValue(msg);
      editArea.setFocus(true);
      cancel.setVisible(!isNew());
      expandText();
      if (msg.length() > 0) {
        Scheduler.get().scheduleFixedDelay(new RepeatingCommand() {
          @Override
          public boolean execute() {
            editArea.setCursorPos(msg.length());
            return false;
          }
        }, 0);
      }
    } else {
      expandTimer.cancel();
    }
    resizePaddingWidget();
  }

  void registerReplyToBox(PublishedBox box) {
    replyToBox = box;
  }

  @Override
  protected void onUnload() {
    expandTimer.cancel();
    super.onUnload();
  }

  private void removeUI() {
    if (replyToBox != null) {
      replyToBox.unregisterReplyBox();
    }
    Side side = comment.side();
    removeFromParent();
    if (!getCommentInfo().has_line()) {
      parent.removeFileCommentBox(this, side);
      return;
    }
    PaddingManager manager = getPaddingManager();
    manager.remove(this);
    parent.removeDraft(this, side, comment.line() - 1);
    cm.focus();
    getSelfWidgetWrapper().getWidget().clear();
    Scheduler.get().scheduleDeferred(new ScheduledCommand() {
      @Override
      public void execute() {
        resizePaddingWidget();
      }
    });
  }

  @UiHandler("message")
  void onMessageClick(ClickEvent e) {
    e.stopPropagation();
  }

  @UiHandler("message")
  void onMessageDoubleClick(DoubleClickEvent e) {
    setEdit(true);
  }

  @UiHandler("edit")
  void onEdit(ClickEvent e) {
    e.stopPropagation();
    setEdit(true);
  }

  @UiHandler("save")
  void onSave(ClickEvent e) {
    e.stopPropagation();
    onSave();
  }

  private void onSave() {
    String message = editArea.getValue().trim();
    if (message.length() == 0) {
      return;
    }

    CommentInfo original = comment;
    CommentInput input = CommentInput.create(original);
    input.setMessage(message);
    enableEdit(false);

    GerritCallback<CommentInfo> cb = new GerritCallback<CommentInfo>() {
      @Override
      public void onSuccess(CommentInfo result) {
        enableEdit(true);
        set(result);
        if (result.message().length() < 70) {
          UIObject.setVisible(p_edit, false);
          setOpen(false);
        } else {
          setEdit(false);
        }
      }

      @Override
      public void onFailure(Throwable e) {
        enableEdit(true);
        super.onFailure(e);
      }
    };
    if (original.id() == null) {
      CommentApi.createDraft(psId, input, cb);
    } else {
      CommentApi.updateDraft(psId, original.id(), input, cb);
    }
    cm.focus();
  }

  private void enableEdit(boolean on) {
    editArea.setEnabled(on);
    save.setEnabled(on);
    cancel.setEnabled(on);
    discard2.setEnabled(on);
  }

  @UiHandler("cancel")
  void onCancel(ClickEvent e) {
    e.stopPropagation();
    if (isNew() && !isDirty()) {
      removeUI();
    } else {
      setEdit(false);
      cm.focus();
    }
  }

  @UiHandler({"discard1", "discard2"})
  void onDiscard(ClickEvent e) {
    e.stopPropagation();
    if (isNew()) {
      removeUI();
    } else {
      setEdit(false);
      CommentApi.deleteDraft(psId, comment.id(),
          new GerritCallback<JavaScriptObject>() {
        @Override
        public void onSuccess(JavaScriptObject result) {
          removeUI();
        }
      });
    }
  }

  @UiHandler("editArea")
  void onKeyDown(KeyDownEvent e) {
    if ((e.isControlKeyDown() || e.isMetaKeyDown())
        && !e.isAltKeyDown() && !e.isShiftKeyDown()) {
      switch (e.getNativeKeyCode()) {
        case 's':
        case 'S':
          e.preventDefault();
          onSave();
          return;
      }
    } else if (e.getNativeKeyCode() == KeyCodes.KEY_ESCAPE && !isDirty()) {
      if (isNew()) {
        removeUI();
        return;
      } else {
        setEdit(false);
        cm.focus();
        return;
      }
    }
    expandTimer.schedule(250);
  }

  private boolean isNew() {
    return comment.id() == null;
  }

  private boolean isDirty() {
    String msg = editArea.getValue().trim();
    if (isNew()) {
      return msg.length() > 0;
    }
    return !msg.equals(comment.message() != null
        ? comment.message().trim()
        : "");
  }
}
