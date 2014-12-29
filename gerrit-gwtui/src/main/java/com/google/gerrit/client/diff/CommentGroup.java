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

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.user.client.DOM;
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
 * When a comment is placed on a line a CommentWidget is created on both sides.
 * The group tracks all comment boxes on that same line, and also includes an
 * empty padding element to keep subsequent lines vertically aligned.
 */
class CommentGroup extends Composite {
  static void pair(CommentGroup a, CommentGroup b) {
    a.peer = b;
    b.peer = a;
  }

  private final CommentManager manager;
  private final CodeMirror cm;
  private final int line;
  private final FlowPanel comments;
  private final Element padding;
  private LineWidget lineWidget;
  private Timer resizeTimer;
  private CommentGroup peer;

  CommentGroup(CommentManager manager, CodeMirror cm, int line) {
    this.manager = manager;
    this.cm = cm;
    this.line = line;

    comments = new FlowPanel();
    comments.setStyleName(Resources.I.style().commentWidgets());
    comments.setVisible(false);
    initWidget(new SimplePanel(comments));

    padding = DOM.createDiv();
    padding.setClassName(DiffTable.style.padding());
    ChunkManager.focusOnClick(padding, cm.side());
    getElement().appendChild(padding);
  }

  CommentManager getCommentManager() {
    return manager;
  }

  CodeMirror getCm() {
    return cm;
  }

  CommentGroup getPeer() {
    return peer;
  }

  int getLine() {
    return line;
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

    if (0 < getBoxCount() || 0 < peer.getBoxCount()) {
      resize();
    } else {
      detach();
      peer.detach();
    }
  }

  private void detach() {
    if (lineWidget != null) {
      lineWidget.clear();
      lineWidget = null;
      updateSelection();
    }
    manager.clearLine(cm.side(), line, this);
    removeFromParent();
  }

  void attachPair(DiffTable parent) {
    if (lineWidget == null && peer.lineWidget == null) {
      this.attach(parent);
      peer.attach(parent);
    }
  }

  private void attach(DiffTable parent) {
    parent.add(this);
    lineWidget = cm.addLineWidget(Math.max(0, line - 1), getElement(),
        Configuration.create()
          .set("coverGutter", true)
          .set("noHScroll", true)
          .set("above", line <= 0)
          .set("insertAt", 0));
  }

  void handleRedraw() {
    lineWidget.onRedraw(new Runnable() {
      @Override
      public void run() {
        if (canComputeHeight() && peer.canComputeHeight()) {
          if (resizeTimer != null) {
            resizeTimer.cancel();
            resizeTimer = null;
          }
          adjustPadding(CommentGroup.this, peer);
        } else if (resizeTimer == null) {
          resizeTimer = new Timer() {
            @Override
            public void run() {
              if (canComputeHeight() && peer.canComputeHeight()) {
                cancel();
                resizeTimer = null;
                adjustPadding(CommentGroup.this, peer);
              }
            }
          };
          resizeTimer.scheduleRepeating(5);
        }
      }
    });
  }

  @Override
  protected void onUnload() {
    super.onUnload();
    if (resizeTimer != null) {
      resizeTimer.cancel();
    }
  }

  void resize() {
    if (lineWidget != null) {
      adjustPadding(this, peer);
    }
  }

  private void updateSelection() {
    if (cm.somethingSelected()) {
      FromTo r = cm.getSelectedRange();
      if (r.to().line() >= line) {
        cm.setSelection(r.from(), r.to());
      }
    }
  }

  private boolean canComputeHeight() {
    return !comments.isVisible() || comments.getOffsetHeight() > 0;
  }

  private int computeHeight() {
    if (comments.isVisible()) {
      // Include margin-bottom: 5px from CSS class.
      return comments.getOffsetHeight() + 5;
    }
    return 0;
  }

  private static void adjustPadding(CommentGroup a, CommentGroup b) {
    int apx = a.computeHeight();
    int bpx = b.computeHeight();
    int h = Math.max(apx, bpx);
    a.padding.getStyle().setHeight(Math.max(0, h - apx), Unit.PX);
    b.padding.getStyle().setHeight(Math.max(0, h - bpx), Unit.PX);
    a.lineWidget.changed();
    b.lineWidget.changed();
    a.updateSelection();
    b.updateSelection();
  }
}
