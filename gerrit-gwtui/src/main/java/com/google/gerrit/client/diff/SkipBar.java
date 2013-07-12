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
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;

import net.codemirror.lib.CodeMirror;
import net.codemirror.lib.CodeMirror.LineClassWhere;
import net.codemirror.lib.CodeMirror.LineHandle;
import net.codemirror.lib.Configuration;
import net.codemirror.lib.LineWidget;
import net.codemirror.lib.TextMarker;
import net.codemirror.lib.TextMarker.FromTo;

import java.util.Map;

/** The Widget that handles expanding of skipped lines */
class SkipBar extends Composite {
  interface Binder extends UiBinder<HTMLPanel, SkipBar> {}
  private static Binder uiBinder = GWT.create(Binder.class);
  private static final int NUM_ROWS_TO_EXPAND = 10;
  private static final int UP_DOWN_THRESHOLD = 30;
  private static final Configuration COLLAPSED =
      Configuration.create().set("collapsed", true);

  private LineWidget widget;

  interface SkipBarStyle extends CssResource {
    String isLineWidget();
    String noExpand();
  }

  @UiField(provided=true)
  Anchor skipNum;

  @UiField(provided=true)
  Anchor upArrow;

  @UiField(provided=true)
  Anchor downArrow;

  @UiField
  SkipBarStyle style;

  private TextMarker marker;
  private SkipBar otherBar;
  private CodeMirror cm;
  private int numSkipLines;
  private Map<LineHandle, Integer> hiddenSkipMap;

  SkipBar(CodeMirror cm, Map<LineHandle, Integer> hiddenSkipMap) {
    this.cm = cm;
    this.hiddenSkipMap = hiddenSkipMap;
    skipNum = new Anchor(true);
    upArrow = new Anchor(true);
    downArrow = new Anchor(true);
    initWidget(uiBinder.createAndBindUi(this));
  }

  void setWidget(LineWidget widget) {
    this.widget = widget;
    addStyleName(style.isLineWidget());
  }

  void setMarker(TextMarker marker, int length) {
    this.marker = marker;
    numSkipLines = length;
    skipNum.setText(Integer.toString(length));
    if (checkAndUpdateArrows()) {
      upArrow.setHTML(PatchUtil.M.expandBefore(NUM_ROWS_TO_EXPAND));
      downArrow.setHTML(PatchUtil.M.expandAfter(NUM_ROWS_TO_EXPAND));
    }
  }

  static void link(SkipBar barA, SkipBar barB) {
    barA.otherBar = barB;
    barB.otherBar = barA;
  }

  private void updateSkipNum() {
    numSkipLines -= NUM_ROWS_TO_EXPAND;
    skipNum.setText(String.valueOf(numSkipLines));
    checkAndUpdateArrows();
  }

  private boolean checkAndUpdateArrows() {
    if (numSkipLines <= UP_DOWN_THRESHOLD) {
      upArrow.addStyleName(style.noExpand());
      downArrow.addStyleName(style.noExpand());
      return false;
    }
    return true;
  }

  private void clearMarkerAndWidget() {
    marker.clear();
    if (widget != null) {
      widget.clear();
    } else {
      cm.removeLineClass(0, LineClassWhere.WRAP, DiffTable.style.hideNumber());
    }
  }

  private void expandAll() {
    hiddenSkipMap.remove(
        cm.getLineHandle(marker.find().getTo().getLine()));
    clearMarkerAndWidget();
    removeFromParent();
  }

  private void expandBefore() {
    FromTo fromTo = marker.find();
    int oldStart = fromTo.getFrom().getLine();
    int newStart = oldStart + NUM_ROWS_TO_EXPAND;
    int end = fromTo.getTo().getLine();
    clearMarkerAndWidget();
    marker = cm.markText(CodeMirror.pos(newStart), CodeMirror.pos(end), COLLAPSED);
    Configuration config = Configuration.create().set("coverGutter", true);
    LineWidget newWidget = cm.addLineWidget(newStart, getElement(), config);
    setWidget(newWidget);
    updateSkipNum();
    hiddenSkipMap.put(cm.getLineHandle(end), numSkipLines);
  }

  private void expandAfter() {
    FromTo fromTo = marker.find();
    int oldEnd = fromTo.getTo().getLine();
    int newEnd = oldEnd - NUM_ROWS_TO_EXPAND;
    marker.clear();
    marker = cm.markText(CodeMirror.pos(fromTo.getFrom().getLine()),
        CodeMirror.pos(newEnd),
        COLLAPSED);
    updateSkipNum();
    hiddenSkipMap.remove(cm.getLineHandle(oldEnd));
    hiddenSkipMap.put(cm.getLineHandle(newEnd), numSkipLines);
  }

  @UiHandler("skipNum")
  void onExpandAll(ClickEvent e) {
    otherBar.expandAll();
    expandAll();
  }

  @UiHandler("upArrow")
  void onExpandBefore(ClickEvent e) {
    otherBar.expandBefore();
    expandBefore();
  }

  @UiHandler("downArrow")
  void onExpandAfter(ClickEvent e) {
    otherBar.expandAfter();
    expandAfter();
  }
}
