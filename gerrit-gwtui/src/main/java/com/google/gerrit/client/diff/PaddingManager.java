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

import net.codemirror.lib.LineWidget;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages paddings for CommentBoxes. An instance of PaddingManager maintains
 * a padding widget whose height changes as necessary. It calculates padding
 * by taking the difference of the sum of CommentBox heights on the two sides.
 *
 * Note that in the case of an insertion or deletion gap, a PaddingManager
 * can map to a list of managers on the other side. The padding needed is then
 * calculated from the sum of all their heights.
 *
 * TODO: Let PaddingManager also take care of the paddings introduced by
 * insertions and deletions.
 */
class PaddingManager {
  private List<CommentBox> comments;
  private LineWidgetElementPair padding;
  private List<PaddingManager> others;

  PaddingManager(LineWidgetElementPair padding) {
    comments = new ArrayList<CommentBox>();
    others = new ArrayList<PaddingManager>();
    this.padding = padding;
  }

  static void link(PaddingManager a, PaddingManager b) {
    if (!a.others.contains(b)) {
      a.others.add(b);
    }
    if (!b.others.contains(a)) {
      b.others.add(a);
    }
  }

  private int getMyTotalHeight() {
    int total = 0;
    for (CommentBox box : comments) {
      total += box.getOffsetHeight();
    }
    return total;
  }

  private int getGroupTotalHeight() {
    if (others.size() > 1) {
      return getMyTotalHeight();
    } else {
      return others.get(0).getOthersTotalHeight();
    }
  }

  private int getOthersTotalHeight() {
    int total = 0;
    for (PaddingManager manager : others) {
      total += manager.getMyTotalHeight();
    }
    return total;
  }

  private void setPaddingHeight(int height) {
    padding.element.getStyle().setHeight(height, Unit.PX);
    padding.widget.changed();
  }

  void resizePaddingWidget() {
    if (others.isEmpty()) {
      throw new IllegalStateException("resizePaddingWidget() called before linking");
    }
    int myHeight = getGroupTotalHeight();
    int othersHeight = getOthersTotalHeight();
    int paddingNeeded = othersHeight - myHeight;
    if (paddingNeeded < 0) {
      for (PaddingManager manager : others.get(0).others) {
        manager.setPaddingHeight(0);
      }
      others.get(others.size() - 1).setPaddingHeight(-paddingNeeded);
    } else {
      setPaddingHeight(paddingNeeded);
      for (PaddingManager other : others) {
        other.setPaddingHeight(0);
      }
    }
  }

  /** This is unused now because threading info is ignored. */
  int getReplyIndex(CommentBox box) {
    return comments.indexOf(box) + 1;
  }

  int getCurrentCount() {
    return comments.size();
  }

  void insert(CommentBox box, int index) {
    comments.add(index, box);
  }

  void remove(CommentBox box) {
    comments.remove(box);
  }

  static class LineWidgetElementPair {
    private LineWidget widget;
    private Element element;

    LineWidgetElementPair(LineWidget w, Element e) {
      widget = w;
      element = e;
    }
  }
}
