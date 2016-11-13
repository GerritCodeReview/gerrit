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
import java.util.PriorityQueue;
import net.codemirror.lib.CodeMirror;

/**
 * LineWidget attached to a CodeMirror container.
 *
 * <p>When a comment is placed on a line a CommentWidget is created on both sides. The group tracks
 * all comment boxes on that same line, and also includes an empty padding element to keep
 * subsequent lines vertically aligned.
 */
class SideBySideCommentGroup extends CommentGroup implements Comparable<SideBySideCommentGroup> {
  static void pair(SideBySideCommentGroup a, SideBySideCommentGroup b) {
    a.peers.add(b);
    b.peers.add(a);
  }

  private final Element padding;
  private final PriorityQueue<SideBySideCommentGroup> peers;

  SideBySideCommentGroup(
      SideBySideCommentManager manager, CodeMirror cm, DisplaySide side, int line) {
    super(manager, cm, side, line);

    padding = DOM.createDiv();
    padding.setClassName(SideBySideTable.style.padding());
    SideBySideChunkManager.focusOnClick(padding, cm.side());
    getElement().appendChild(padding);
    peers = new PriorityQueue<>();
  }

  SideBySideCommentGroup getPeer() {
    return peers.peek();
  }

  @Override
  void remove(DraftBox box) {
    super.remove(box);

    if (getBoxCount() == 0 && peers.size() == 1 && peers.peek().peers.size() > 1) {
      SideBySideCommentGroup peer = peers.peek();
      peer.peers.remove(this);
      detach();
      if (peer.getBoxCount() == 0
          && peer.peers.size() == 1
          && peer.peers.peek().getBoxCount() == 0) {
        peer.detach();
      } else {
        peer.resize();
      }
    } else {
      resize();
    }
  }

  @Override
  void init(DiffTable parent) {
    if (getLineWidget() == null) {
      attach(parent);
    }
    for (CommentGroup peer : peers) {
      if (peer.getLineWidget() == null) {
        peer.attach(parent);
      }
    }
  }

  @Override
  void handleRedraw() {
    getLineWidget()
        .onRedraw(
            new Runnable() {
              @Override
              public void run() {
                if (canComputeHeight() && peers.peek().canComputeHeight()) {
                  if (getResizeTimer() != null) {
                    getResizeTimer().cancel();
                    setResizeTimer(null);
                  }
                  adjustPadding(SideBySideCommentGroup.this, peers.peek());
                } else if (getResizeTimer() == null) {
                  setResizeTimer(
                      new Timer() {
                        @Override
                        public void run() {
                          if (canComputeHeight() && peers.peek().canComputeHeight()) {
                            cancel();
                            setResizeTimer(null);
                            adjustPadding(SideBySideCommentGroup.this, peers.peek());
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
      adjustPadding(this, peers.peek());
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
    for (SideBySideCommentGroup otherPeer : a.peers) {
      if (otherPeer != b) {
        bpx += otherPeer.computeHeight();
      }
    }
    for (SideBySideCommentGroup otherPeer : b.peers) {
      if (otherPeer != a) {
        apx += otherPeer.computeHeight();
      }
    }
    int h = Math.max(apx, bpx);
    a.padding.getStyle().setHeight(Math.max(0, h - apx), Unit.PX);
    b.padding.getStyle().setHeight(Math.max(0, h - bpx), Unit.PX);
    a.getLineWidget().changed();
    b.getLineWidget().changed();
    a.updateSelection();
    b.updateSelection();
  }

  @Override
  public int compareTo(SideBySideCommentGroup o) {
    if (side == o.side) {
      return line - o.line;
    }
    throw new IllegalStateException("Cannot compare SideBySideCommentGroup with different sides");
  }
}
