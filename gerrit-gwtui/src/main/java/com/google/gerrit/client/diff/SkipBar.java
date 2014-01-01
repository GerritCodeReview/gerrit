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

import com.google.gerrit.client.patches.PatchUtil;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;

import net.codemirror.lib.CodeMirror;
import net.codemirror.lib.Configuration;
import net.codemirror.lib.LineWidget;
import net.codemirror.lib.TextMarker;
import net.codemirror.lib.TextMarker.FromTo;

/** The Widget that handles expanding of skipped lines */
class SkipBar extends Composite {
  interface Binder extends UiBinder<HTMLPanel, SkipBar> {}
  private static final Binder uiBinder = GWT.create(Binder.class);
  private static final int NUM_ROWS_TO_EXPAND = 10;
  private static final int UP_DOWN_THRESHOLD = 30;

  interface SkipBarStyle extends CssResource {
    String noExpand();
  }

  @UiField(provided=true) Anchor skipNum;
  @UiField(provided=true) Anchor upArrow;
  @UiField(provided=true) Anchor downArrow;
  @UiField SkipBarStyle style;

  private final SkipManager manager;
  private final CodeMirror cm;

  private int start;
  private LineWidget lineWidget;
  private TextMarker textMarker;
  private SkipBar otherBar;

  SkipBar(SkipManager manager, final CodeMirror cm) {
    this.manager = manager;
    this.cm = cm;

    skipNum = new Anchor(true);
    upArrow = new Anchor(true);
    downArrow = new Anchor(true);
    initWidget(uiBinder.createAndBindUi(this));
    addDomHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        cm.focus();
      }
    }, ClickEvent.getType());
  }

  int getStart() {
    return start;
  }

  DisplaySide getSide() {
    return cm.side();
  }

  boolean contains(int line) {
    FromTo r = textMarker.find();
    return r.getFrom().getLine() <= line && line <= r.getTo().getLine();
  }

  void ensureLineVisible(int line, int context) {
    FromTo r = textMarker.find();
    int start = r.getFrom().getLine();
    int end = r.getTo().getLine();

    int a = line - context;
    int b = line + context;

    // Don't leave just one line skipped at start or end.
    if (start == a - 1) {
      a = start;
    }
    if (end == b + 1) {
      b = end;
    }

    if (a <= start && end <= b) {
      expandAll();
    } else if (a <= start && b < end) {
      expandBefore(b - start);
    } else if (start < a && end <= b) {
      expandAfter(end - a);
    } else {
      expandAfter(end - a);
      manager.skip(cm.side(), b, end);
    }
  }

  void collapse(int start, int end, boolean attach) {
    if (attach) {
      boolean isNew = lineWidget == null;
      Configuration cfg = Configuration.create()
          .set("coverGutter", true)
          .set("noHScroll", true);
      if (start == 0) { // First line workaround
        lineWidget = cm.addLineWidget(end + 1, getElement(), cfg.set("above", true));
      } else {
        lineWidget = cm.addLineWidget(start - 1, getElement(), cfg);
      }
      if (isNew) {
        lineWidget.onFirstRedraw(new Runnable() {
          @Override
          public void run() {
            int w = cm.getGutterElement().getOffsetWidth();
            getElement().getStyle().setPaddingLeft(w, Unit.PX);
          }
        });
      } else {
        manager.move(this, this.start, start);
      }
      this.start = start;
    }

    textMarker = cm.markText(
        CodeMirror.pos(start, 0),
        CodeMirror.pos(end),
        Configuration.create()
          .set("collapsed", true)
          .set("inclusiveLeft", true)
          .set("inclusiveRight", true));

    int skipped = end - start + 1;
    if (skipped <= UP_DOWN_THRESHOLD) {
      addStyleName(style.noExpand());
    } else {
      upArrow.setHTML(PatchUtil.M.expandBefore(NUM_ROWS_TO_EXPAND));
      downArrow.setHTML(PatchUtil.M.expandAfter(NUM_ROWS_TO_EXPAND));
    }
    skipNum.setText(Integer.toString(skipped));
  }

  static void link(SkipBar barA, SkipBar barB) {
    barA.otherBar = barB;
    barB.otherBar = barA;
  }

  private void clearMarkerAndWidget() {
    textMarker.clear();
    lineWidget.clear();
  }

  void expandBefore(int cnt) {
    expandSideBefore(cnt);
    otherBar.expandSideBefore(cnt);
  }

  private void expandSideBefore(int cnt) {
    FromTo range = textMarker.find();
    int oldStart = range.getFrom().getLine();
    int newStart = oldStart + cnt;
    int end = range.getTo().getLine();
    clearMarkerAndWidget();
    collapse(newStart, end, true);
    updateSelection();
  }

  void expandAll() {
    expandSideAll();
    otherBar.expandSideAll();
  }

  private void expandSideAll() {
    clearMarkerAndWidget();
    removeFromParent();
    updateSelection();
  }

  void expandAfter(int cnt) {
    expandSideAfter(cnt);
    otherBar.expandSideAfter(cnt);
  }

  private void expandSideAfter(int cnt) {
    FromTo range = textMarker.find();
    int start = range.getFrom().getLine();
    int oldEnd = range.getTo().getLine();
    int newEnd = oldEnd - cnt;
    textMarker.clear();
    collapse(start, newEnd, false);
    updateSelection();
  }

  private void updateSelection() {
    if (cm.somethingSelected()) {
      FromTo sel = cm.getSelectedRange();
      cm.setSelection(sel.getFrom(), sel.getTo());
    }
  }

  @UiHandler("skipNum")
  void onExpandAll(ClickEvent e) {
    expandAll();
    manager.remove(this, otherBar);
    cm.focus();
  }

  @UiHandler("upArrow")
  void onExpandBefore(ClickEvent e) {
    expandBefore(NUM_ROWS_TO_EXPAND);
    cm.focus();
  }

  @UiHandler("downArrow")
  void onExpandAfter(ClickEvent e) {
    expandAfter(NUM_ROWS_TO_EXPAND);
    cm.focus();
  }
}
