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

import net.codemirror.lib.LineWidget;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages paddings for CommentBoxes. Each line that may need to be padded owns
 * a PaddingManager instance, which maintains a padding widget whose height
 * changes as necessary. PaddingManager calculates padding by taking the
 * difference of the sum of CommentBox heights on the two sides.
 *
 * Note that in the case of an insertion or deletion gap, A PaddingManager
 * can map to a list of managers on the other side. The padding needed is then
 * calculated from the sum of all their heights.
 *
 * TODO: Let PaddingManager also take care of the paddings introduced by
 * insertions and deletions.
 */
class PaddingManager {
  private List<CommentBox> comments;
  private PaddingWidgetWrapper wrapper;
  private List<PaddingManager> others;

  PaddingManager(PaddingWidgetWrapper padding) {
    comments = new ArrayList<CommentBox>();
    others = new ArrayList<PaddingManager>();
    this.wrapper = padding;
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
      /**
       * This gets the height of CM's line widget div, taking the margin and
       * the horizontal scrollbar into account.
       */
      total += box.getSelfWidgetWrapper().getElement().getParentElement().getOffsetHeight();
    }
    return total;
  }

  /**
   * If this instance is on the insertion side, its counterpart on the other
   * side will map to a group of PaddingManagers on this side, so we calculate
   * the group's total height instead of an individual one's.
   */
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
    SideBySide2.setHeightInPx(wrapper.element, height);
    wrapper.widget.changed();
  }

  void resizePaddingWidget() {
    if (others.isEmpty()) {
      return;
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

  static class PaddingWidgetWrapper {
    private LineWidget widget;
    private Element element;

    PaddingWidgetWrapper(LineWidget w, Element e) {
      widget = w;
      element = e;
    }

    LineWidget getWidget() {
      return widget;
    }

    Element getElement() {
      return element;
    }
  }

  static class LinePaddingWidgetWrapper extends PaddingWidgetWrapper {
    private int chunkLength;
    private int otherLine;

    LinePaddingWidgetWrapper(PaddingWidgetWrapper pair, int otherLine, int chunkLength) {
      super(pair.widget, pair.element);

      this.otherLine = otherLine;
      this.chunkLength = chunkLength;
    }

    int getChunkLength() {
      return chunkLength;
    }

    int getOtherLine() {
      return otherLine;
    }
  }
}
