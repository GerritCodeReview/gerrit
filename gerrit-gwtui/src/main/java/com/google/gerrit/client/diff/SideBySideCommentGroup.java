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

import net.codemirror.lib.CodeMirror;

/**
 * LineWidget attached to a CodeMirror container.
 *
 * When a comment is placed on a line a CommentWidget is created on both sides.
 * The group tracks all comment boxes on that same line, and also includes an
 * empty padding element to keep subsequent lines vertically aligned.
 */
class SideBySideCommentGroup extends CommentGroup {
  static void pair(SideBySideCommentGroup a, SideBySideCommentGroup b) {
    a.peer = b;
    b.peer = a;
  }

  private final Element padding;
  private SideBySideCommentGroup peer;

  SideBySideCommentGroup(SideBySideCommentManager manager, CodeMirror cm, int line) {
    super(manager, cm, line);

    padding = DOM.createDiv();
    padding.setClassName(SideBySideTable.style.padding());
    SideBySideChunkManager.focusOnClick(padding, cm.side());
    getElement().appendChild(padding);
  }

  SideBySideCommentGroup getPeer() {
    return peer;
  }

  @Override
  void remove(DraftBox box) {
    super.remove(box);

    if (0 < getBoxCount() || 0 < peer.getBoxCount()) {
      resize();
    } else {
      detach();
      peer.detach();
    }
  }

  void attachPair(DiffTable parent) {
    if (getLineWidget() == null && peer.getLineWidget() == null) {
      this.attach(parent);
      peer.attach(parent);
    }
  }

  @Override
  void handleRedraw() {
    getLineWidget().onRedraw(new Runnable() {
      @Override
      public void run() {
        if (canComputeHeight() && peer.canComputeHeight()) {
          if (getResizeTimer() != null) {
            getResizeTimer().cancel();
            setResizeTimer(null);
          }
          adjustPadding(SideBySideCommentGroup.this, peer);
        } else if (getResizeTimer() == null) {
          setResizeTimer(new Timer() {
            @Override
            public void run() {
              if (canComputeHeight() && peer.canComputeHeight()) {
                cancel();
                setResizeTimer(null);
                adjustPadding(SideBySideCommentGroup.this, peer);
              }
            }
          });
          getResizeTimer().scheduleRepeating(5);
        }
      }
    });
  }

  @Override
  void resize() {
    if (getLineWidget() != null) {
      adjustPadding(this, peer);
    }
  }

  private int computeHeight() {
    if (getComments().isVisible()) {
      // Include margin-bottom: 5px from CSS class.
      return getComments().getOffsetHeight() + 5;
    }
    return 0;
  }

  private static void adjustPadding(SideBySideCommentGroup a, SideBySideCommentGroup b) {
    int apx = a.computeHeight();
    int bpx = b.computeHeight();
    int h = Math.max(apx, bpx);
    a.padding.getStyle().setHeight(Math.max(0, h - apx), Unit.PX);
    b.padding.getStyle().setHeight(Math.max(0, h - bpx), Unit.PX);
    a.getLineWidget().changed();
    b.getLineWidget().changed();
    a.updateSelection();
    b.updateSelection();
  }
}
