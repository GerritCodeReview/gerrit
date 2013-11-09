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
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
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
  private static final Configuration MARKER_CONFIG = Configuration.create()
      .set("collapsed", true)
      .set("inclusiveLeft", true)
      .set("inclusiveRight", true);

  private LineWidget widget;

  interface SkipBarStyle extends CssResource {
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

  SkipBar(CodeMirror cmInstance) {
    cm = cmInstance;
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

  void setWidget(LineWidget lineWidget) {
    widget = lineWidget;
    Scheduler.get().scheduleDeferred(new ScheduledCommand(){
      @Override
      public void execute() {
        getElement().getStyle().setPaddingLeft(
            cm.getGutterElement().getOffsetWidth(), Unit.PX);
      }
    });
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
      addStyleName(style.noExpand());
      return false;
    }
    return true;
  }

  private void clearMarkerAndWidget() {
    marker.clear();
    assert (widget != null);
    widget.clear();
  }

  private void expandAll() {
    clearMarkerAndWidget();
    removeFromParent();
    cm.focus();
  }

  private void expandBefore() {
    FromTo fromTo = marker.find();
    int oldStart = fromTo.getFrom().getLine();
    int newStart = oldStart + NUM_ROWS_TO_EXPAND;
    int end = fromTo.getTo().getLine();
    clearMarkerAndWidget();
    marker = cm.markText(
        CodeMirror.pos(newStart, 0), CodeMirror.pos(end), MARKER_CONFIG);
    Configuration config = Configuration.create()
        .set("coverGutter", true)
        .set("noHScroll", true);
    setWidget(cm.addLineWidget(newStart - 1, getElement(), config));
    updateSkipNum();
    cm.focus();
  }

  private void expandAfter() {
    FromTo fromTo = marker.find();
    int start = fromTo.getFrom().getLine();
    int oldEnd = fromTo.getTo().getLine();
    int newEnd = oldEnd - NUM_ROWS_TO_EXPAND;
    marker.clear();
    marker = cm.markText(
        CodeMirror.pos(start, 0), CodeMirror.pos(newEnd), MARKER_CONFIG);
    if (start == 0) { // First line workaround
      Configuration config = Configuration.create()
          .set("coverGutter", true)
          .set("noHScroll", true)
          .set("above", true);
      widget.clear();
      setWidget(cm.addLineWidget(newEnd + 1, getElement(), config));
    }
    updateSkipNum();
    cm.focus();
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
