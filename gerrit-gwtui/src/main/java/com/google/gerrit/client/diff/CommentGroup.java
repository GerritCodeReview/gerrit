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

import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.SimplePanel;
import net.codemirror.lib.CodeMirror;
import net.codemirror.lib.Configuration;
import net.codemirror.lib.LineWidget;
import net.codemirror.lib.TextMarker.FromTo;

/**
 * LineWidget attached to a CodeMirror container.
 *
 * <p>When a comment is placed on a line a CommentWidget is created.
 */
abstract class CommentGroup extends Composite {

  final DisplaySide side;
  final int line;

  private final CommentManager manager;
  private final CodeMirror cm;
  private final FlowPanel comments;
  private LineWidget lineWidget;
  private Timer resizeTimer;

  CommentGroup(CommentManager manager, CodeMirror cm, DisplaySide side, int line) {
    this.manager = manager;
    this.cm = cm;
    this.side = side;
    this.line = line;

    comments = new FlowPanel();
    comments.setStyleName(Resources.I.style().commentWidgets());
    comments.setVisible(false);
    initWidget(new SimplePanel(comments));
  }

  CommentManager getCommentManager() {
    return manager;
  }

  CodeMirror getCm() {
    return cm;
  }

  int getLine() {
    return line;
  }

  DisplaySide getSide() {
    return side;
  }

  void add(PublishedBox box) {
    comments.add(box);
    comments.setVisible(true);
  }

  void add(DraftBox box) {
    PublishedBox p = box.getReplyToBox();
    if (p != null) {
      for (int i = 0; i < getBoxCount(); i++) {
        if (p == getCommentBox(i)) {
          comments.insert(box, i + 1);
          comments.setVisible(true);
          resize();
          return;
        }
      }
    }
    comments.add(box);
    comments.setVisible(true);
    resize();
  }

  CommentBox getCommentBox(int i) {
    return (CommentBox) comments.getWidget(i);
  }

  int getBoxCount() {
    return comments.getWidgetCount();
  }

  void openCloseLast() {
    if (0 < getBoxCount()) {
      CommentBox box = getCommentBox(getBoxCount() - 1);
      box.setOpen(!box.isOpen());
    }
  }

  void openCloseAll() {
    boolean open = false;
    for (int i = 0; i < getBoxCount(); i++) {
      if (!getCommentBox(i).isOpen()) {
        open = true;
        break;
      }
    }
    setOpenAll(open);
  }

  void setOpenAll(boolean open) {
    for (int i = 0; i < getBoxCount(); i++) {
      getCommentBox(i).setOpen(open);
    }
  }

  void remove(DraftBox box) {
    comments.remove(box);
    comments.setVisible(0 < getBoxCount());
  }

  void detach() {
    if (lineWidget != null) {
      lineWidget.clear();
      lineWidget = null;
      updateSelection();
    }
    manager.clearLine(side, line, this);
    removeFromParent();
  }

  void attach(DiffTable parent) {
    parent.add(this);
    lineWidget =
        cm.addLineWidget(
            Math.max(0, line - 1),
            getElement(),
            Configuration.create()
                .set("coverGutter", true)
                .set("noHScroll", true)
                .set("above", line <= 0)
                .set("insertAt", 0));
  }

  @Override
  protected void onUnload() {
    super.onUnload();
    if (resizeTimer != null) {
      resizeTimer.cancel();
    }
  }

  void updateSelection() {
    if (cm.somethingSelected()) {
      FromTo r = cm.getSelectedRange();
      if (r.to().line() >= line) {
        cm.setSelection(r.from(), r.to());
      }
    }
  }

  boolean canComputeHeight() {
    return !comments.isVisible() || comments.getOffsetHeight() > 0;
  }

  LineWidget getLineWidget() {
    return lineWidget;
  }

  void setLineWidget(LineWidget widget) {
    lineWidget = widget;
  }

  Timer getResizeTimer() {
    return resizeTimer;
  }

  void setResizeTimer(Timer timer) {
    resizeTimer = timer;
  }

  FlowPanel getComments() {
    return comments;
  }

  CommentManager getManager() {
    return manager;
  }

  abstract void init(DiffTable parent);

  abstract void handleRedraw();

  abstract void resize();
}
