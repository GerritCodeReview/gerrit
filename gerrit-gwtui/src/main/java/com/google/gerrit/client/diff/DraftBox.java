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

package com.google.gerrit.client.diff;

import com.google.gerrit.client.FormatUtil;
import com.google.gerrit.client.change.LocalComments;
import com.google.gerrit.client.changes.CommentApi;
import com.google.gerrit.client.changes.CommentInfo;
import com.google.gerrit.client.rpc.CallbackGroup;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gerrit.client.ui.CommentLinkProcessor;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.BlurEvent;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.DoubleClickEvent;
import com.google.gwt.event.dom.client.DoubleClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.MouseMoveEvent;
import com.google.gwt.event.dom.client.MouseMoveHandler;
import com.google.gwt.event.dom.client.MouseUpEvent;
import com.google.gwt.event.dom.client.MouseUpHandler;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.UIObject;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtexpui.globalkey.client.NpTextArea;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;
import net.codemirror.lib.CodeMirror;

/** An HtmlPanel for displaying and editing a draft */
class DraftBox extends CommentBox {
  interface Binder extends UiBinder<HTMLPanel, DraftBox> {}

  private static final Binder uiBinder = GWT.create(Binder.class);

  private static final int INITIAL_LINES = 5;
  private static final int MAX_LINES = 30;

  private final CommentLinkProcessor linkProcessor;
  private final PatchSet.Id psId;
  private final Project.NameKey project;
  private final boolean expandAll;
  private CommentInfo comment;
  private PublishedBox replyToBox;
  private Timer expandTimer;
  private Timer resizeTimer;
  private int editAreaHeight;
  private boolean autoClosed;
  private CallbackGroup pendingGroup;

  @UiField Widget header;
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
      CommentGroup group,
      CommentLinkProcessor clp,
      @Nullable Project.NameKey pj,
      PatchSet.Id id,
      CommentInfo info,
      boolean expandAllComments) {
    super(group, info.range());

    linkProcessor = clp;
    psId = id;
    project = pj;
    expandAll = expandAllComments;
    initWidget(uiBinder.createAndBindUi(this));

    expandTimer =
        new Timer() {
          @Override
          public void run() {
            expandText();
          }
        };
    set(info);

    header.addDomHandler(
        new ClickHandler() {
          @Override
          public void onClick(ClickEvent event) {
            if (!isEdit()) {
              if (autoClosed && !isOpen()) {
                setOpen(true);
                setEdit(true);
              } else {
                setOpen(!isOpen());
              }
            }
          }
        },
        ClickEvent.getType());

    addDomHandler(
        new DoubleClickHandler() {
          @Override
          public void onDoubleClick(DoubleClickEvent event) {
            if (isEdit()) {
              editArea.setFocus(true);
            } else {
              setOpen(true);
              setEdit(true);
            }
          }
        },
        DoubleClickEvent.getType());

    initResizeHandler();
  }

  private void set(CommentInfo info) {
    autoClosed = !expandAll && info.message() != null && info.message().length() < 70;
    date.setInnerText(FormatUtil.shortFormatDayTime(info.updated()));
    if (info.message() != null) {
      String msg = info.message().trim();
      summary.setInnerText(msg);
      message.setHTML(linkProcessor.apply(new SafeHtmlBuilder().append(msg).wikify()));
    }
    comment = info;
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
    editAreaHeight = editArea.getOffsetHeight();
    getCommentGroup().resize();
  }

  boolean isEdit() {
    return UIObject.isVisible(p_edit);
  }

  void setEdit(boolean edit) {
    UIObject.setVisible(summary, false);
    UIObject.setVisible(p_view, !edit);
    UIObject.setVisible(p_edit, edit);

    setRangeHighlight(edit);
    if (edit) {
      String msg = comment.message() != null ? comment.message() : "";
      editArea.setValue(msg);
      cancel.setVisible(!isNew());
      expandText();
      editAreaHeight = editArea.getOffsetHeight();

      final int len = msg.length();
      Scheduler.get()
          .scheduleDeferred(
              new ScheduledCommand() {
                @Override
                public void execute() {
                  editArea.setFocus(true);
                  if (len > 0) {
                    editArea.setCursorPos(len);
                  }
                }
              });
    } else {
      expandTimer.cancel();
      resizeTimer.cancel();
    }
    getCommentManager().setUnsaved(this, edit);
    getCommentGroup().resize();
  }

  PublishedBox getReplyToBox() {
    return replyToBox;
  }

  void setReplyToBox(PublishedBox box) {
    replyToBox = box;
  }

  @Override
  protected void onUnload() {
    expandTimer.cancel();
    resizeTimer.cancel();
    super.onUnload();
  }

  private void removeUI() {
    if (replyToBox != null) {
      replyToBox.unregisterReplyBox();
    }

    getCommentManager().setUnsaved(this, false);
    setRangeHighlight(false);
    clearRange();
    getAnnotation().remove();
    getCommentGroup().remove(this);
    getCm().focus();
  }

  private void restoreSelection() {
    if (getFromTo() != null && comment.inReplyTo() == null) {
      getCm().setSelection(getFromTo().from(), getFromTo().to());
    }
  }

  @UiHandler("message")
  void onMessageClick(ClickEvent e) {
    e.stopPropagation();
  }

  @UiHandler("message")
  void onMessageDoubleClick(@SuppressWarnings("unused") DoubleClickEvent e) {
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
    CallbackGroup group = new CallbackGroup();
    save(group);
    group.done();
  }

  void save(CallbackGroup group) {
    if (pendingGroup != null) {
      pendingGroup.addListener(group);
      return;
    }

    String message = editArea.getValue().trim();
    if (message.length() == 0) {
      return;
    }

    CommentInfo input = CommentInfo.copy(comment);
    input.message(message);
    enableEdit(false);

    pendingGroup = group;
    final LocalComments lc = new LocalComments(project, psId);
    GerritCallback<CommentInfo> cb =
        new GerritCallback<CommentInfo>() {
          @Override
          public void onSuccess(CommentInfo result) {
            enableEdit(true);
            pendingGroup = null;
            set(result);
            setEdit(false);
            if (autoClosed) {
              setOpen(false);
            }
            getCommentManager().setUnsaved(DraftBox.this, false);
          }

          @Override
          public void onFailure(Throwable e) {
            enableEdit(true);
            pendingGroup = null;
            if (RestApi.isNotSignedIn(e)) {
              CommentInfo saved = CommentInfo.copy(comment);
              saved.message(editArea.getValue().trim());
              lc.setInlineComment(saved);
            }
            super.onFailure(e);
          }
        };
    if (input.id() == null) {
      CommentApi.createDraft(psId, Project.NameKey.asStringOrNull(project), input, group.add(cb));
    } else {
      CommentApi.updateDraft(
          psId, Project.NameKey.asStringOrNull(project), input.id(), input, group.add(cb));
    }
    CodeMirror cm = getCm();
    cm.vim().handleKey("<Esc>");
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
      restoreSelection();
    } else {
      setEdit(false);
      if (autoClosed) {
        setOpen(false);
      }
      getCm().focus();
    }
  }

  @UiHandler({"discard1", "discard2"})
  void onDiscard(ClickEvent e) {
    e.stopPropagation();
    if (isNew()) {
      removeUI();
      restoreSelection();
    } else {
      setEdit(false);
      pendingGroup = new CallbackGroup();
      CommentApi.deleteDraft(
          psId,
          Project.NameKey.asStringOrNull(project),
          comment.id(),
          pendingGroup.addFinal(
              new GerritCallback<JavaScriptObject>() {
                @Override
                public void onSuccess(JavaScriptObject result) {
                  pendingGroup = null;
                  removeUI();
                }
              }));
    }
  }

  @UiHandler("editArea")
  void onKeyDown(KeyDownEvent e) {
    resizeTimer.cancel();
    if ((e.isControlKeyDown() || e.isMetaKeyDown()) && !e.isAltKeyDown() && !e.isShiftKeyDown()) {
      switch (e.getNativeKeyCode()) {
        case 's':
        case 'S':
          e.preventDefault();
          CallbackGroup group = new CallbackGroup();
          save(group);
          group.done();
          return;
      }
    } else if (e.getNativeKeyCode() == KeyCodes.KEY_ESCAPE && !isDirty()) {
      if (isNew()) {
        removeUI();
        restoreSelection();
        return;
      }
      setEdit(false);
      if (autoClosed) {
        setOpen(false);
      }
      getCm().focus();
      return;
    }
    expandTimer.schedule(250);
  }

  @UiHandler("editArea")
  void onBlur(@SuppressWarnings("unused") BlurEvent e) {
    resizeTimer.cancel();
  }

  private void initResizeHandler() {
    resizeTimer =
        new Timer() {
          @Override
          public void run() {
            getCommentGroup().resize();
          }
        };

    addDomHandler(
        new MouseMoveHandler() {
          @Override
          public void onMouseMove(MouseMoveEvent event) {
            int h = editArea.getOffsetHeight();
            if (isEdit() && h != editAreaHeight) {
              getCommentGroup().resize();
              resizeTimer.scheduleRepeating(50);
              editAreaHeight = h;
            }
          }
        },
        MouseMoveEvent.getType());

    addDomHandler(
        new MouseUpHandler() {
          @Override
          public void onMouseUp(MouseUpEvent event) {
            resizeTimer.cancel();
            getCommentGroup().resize();
          }
        },
        MouseUpEvent.getType());
  }

  private boolean isNew() {
    return comment.id() == null;
  }

  private boolean isDirty() {
    String msg = editArea.getValue().trim();
    if (isNew()) {
      return msg.length() > 0;
    }
    return !msg.equals(comment.message() != null ? comment.message().trim() : "");
  }
}
