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

import com.google.gerrit.client.changes.ChangeInfo.RevisionInfo;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.dom.client.Element;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.UIObject;
import com.google.gwt.user.client.ui.Widget;

/**
 * A table with one row and two columns to hold the two CodeMirrors displaying
 * the files to be diffed.
 */
class DiffTable extends Composite {
  interface Binder extends UiBinder<HTMLPanel, DiffTable> {}
  private static final Binder uiBinder = GWT.create(Binder.class);

  interface DiffTableStyle extends CssResource {
    String fullscreen();
    String intralineBg();
    String diff();
    String intralineBg_dark();
    String diff_dark();
    String noIntraline();
    String activeLine();
    String range();
    String rangeHighlight();
    String showTabs();
    String showLineNumbers();
    String columnMargin();
    String padding();
  }

  @UiField Element cmA;
  @UiField Element cmB;
  @UiField OverviewBar overview;
  @UiField Element patchSetNavRow;
  @UiField Element patchSetNavCellA;
  @UiField Element patchSetNavCellB;
  @UiField FlowPanel widgets;
  @UiField static DiffTableStyle style;

  @UiField(provided = true)
  PatchSetSelectBox2 patchSetSelectBoxA;

  @UiField(provided = true)
  PatchSetSelectBox2 patchSetSelectBoxB;

  private SideBySide2 parent;
  private boolean headerVisible;

  DiffTable(SideBySide2 parent, PatchSet.Id base, PatchSet.Id revision,
      String path) {
    patchSetSelectBoxA = new PatchSetSelectBox2(
        parent, DisplaySide.A, revision.getParentKey(), base, path);
    patchSetSelectBoxB = new PatchSetSelectBox2(
        parent, DisplaySide.B, revision.getParentKey(), revision, path);
    PatchSetSelectBox2.link(patchSetSelectBoxA, patchSetSelectBoxB);

    initWidget(uiBinder.createAndBindUi(this));
    this.parent = parent;
    this.headerVisible = true;
  }

  boolean isHeaderVisible() {
    return headerVisible;
  }

  void setHeaderVisible(boolean show) {
    headerVisible = show;
    UIObject.setVisible(patchSetNavRow, show);
    if (show) {
      parent.header.removeStyleName(style.fullscreen());
    } else {
      parent.header.addStyleName(style.fullscreen());
    }
    parent.resizeCodeMirror();
  }

  int getHeaderHeight() {
    return patchSetSelectBoxA.getOffsetHeight();
  }

  void setUpPatchSetNav(JsArray<RevisionInfo> list, DiffInfo info) {
    patchSetSelectBoxA.setUpPatchSetNav(list, info.meta_a());
    patchSetSelectBoxB.setUpPatchSetNav(list, info.meta_b());
  }

  void add(Widget widget) {
    widgets.add(widget);
  }
}
