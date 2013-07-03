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
import net.codemirror.lib.Configuration;
import net.codemirror.lib.TextMarker;
import net.codemirror.lib.TextMarker.FromTo;

/** The Widget that handles expanding of skipped lines */
class SkipBar extends Composite {
  interface Binder extends UiBinder<HTMLPanel, SkipBar> {}
  private static Binder uiBinder = GWT.create(Binder.class);
  private static final int NUM_ROWS_TO_EXPAND = 10;
  private static final int UP_DOWN_THRESHOLD = 30;

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

  SkipBar(CodeMirror cm) {
    this.cm = cm;
    skipNum = new Anchor(true);
    upArrow = new Anchor(true);
    downArrow = new Anchor(true);
    initWidget(uiBinder.createAndBindUi(this));
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

  private void expandAll() {
    marker.clear();
    removeFromParent();
  }

  private void updateSkipNum() {
    numSkipLines -= NUM_ROWS_TO_EXPAND;
    skipNum.setText(Integer.toString(numSkipLines));
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

  private void expandBefore() {
    FromTo fromTo = marker.find();
    marker.clear();
    marker = cm.markText(CodeMirror.pos(
        fromTo.getFrom().getLine() + NUM_ROWS_TO_EXPAND),
        CodeMirror.pos(fromTo.getTo().getLine()),
        Configuration.createReplace(getElement()));
    updateSkipNum();
  }

  private void expandAfter() {
    FromTo fromTo = marker.find();
    marker.clear();
    marker = cm.markText(CodeMirror.pos(fromTo.getFrom().getLine()),
        CodeMirror.pos(fromTo.getTo().getLine() - NUM_ROWS_TO_EXPAND),
        Configuration.createReplace(getElement()));
    updateSkipNum();
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
