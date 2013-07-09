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

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style.Unit;

import net.codemirror.lib.LineWidget;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

class CommentBoxManager {
  private List<CommentBox> comments;
  private LineWidgetElementPair padding;
  private List<CommentBoxManager> others;

  CommentBoxManager() {
    comments = new ArrayList<CommentBox>();
    others = new ArrayList<CommentBoxManager>();
  }

  static void link(CommentBoxManager a, CommentBoxManager b) {
    if (!a.others.contains(b)) {
      a.others.add(b);
    }
    if (!b.others.contains(a)) {
      b.others.add(a);
    }
  }

  private int getTotalHeight() {
    int total = 0;
    for (CommentBox box : comments) {
      total += box.getOffsetHeight();
    }
    return total;
  }

  private int getOthersTotalHeight() {
    int total = 0;
    for (CommentBoxManager manager : others) {
      total += manager.getTotalHeight();
    }
    return total;
  }

  void setPaddingWidget(LineWidgetElementPair padding) {
    this.padding = padding;
  }

  void resizePaddingWidget() {
    if (others.isEmpty()) {
      return;
    }
    if (getOthersTotalHeight() == 0 && getTotalHeight() == 0) {
      padding.element.getStyle().setHeight(0, Unit.PX);
      padding.widget.changed();
      return;
    }
    int paddingNeeded = getOthersTotalHeight() - getTotalHeight();
    System.out.println(getOthersTotalHeight() + " " + getTotalHeight() + " " + paddingNeeded);
    if (paddingNeeded < 0) {
      others.get(others.size() - 1).resizePaddingWidget();
    } else {
      padding.element.getStyle().setHeight(paddingNeeded, Unit.PX);
      padding.widget.changed();
    }
  }

  int getReplyIndex(CommentBox box) {
    return comments.indexOf(box) + 1;
  }

  void insert(CommentBox box, int index) {
    comments.add(index, box);
  }

  void remove(CommentBox box) {
    comments.remove(box);
    resizePaddingWidget();
  }

  static class LineWidgetElementPair {
    private LineWidget widget;
    private Element element;

    LineWidgetElementPair(LineWidget w, Element e) {
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
}
