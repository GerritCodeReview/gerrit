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

import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.HTMLPanel;

/**
 * A table with one row and one column to hold a unified CodeMirror displaying
 * the files to be compared.
 */
class UnifiedTable2 extends DiffTable {
  interface Binder extends UiBinder<HTMLPanel, UnifiedTable2> {}
  private static final Binder uiBinder = GWT.create(Binder.class);

  interface DiffTableStyle extends CssResource {
    String fullscreen();
    String intralineInsert();
    String intralineDelete();
    String dark();
    String diffInsert();
    String diffDelete();
    String noIntraline();
    String activeLine();
    String range();
    String rangeHighlight();
    String showTabs();
    String columnMargin();
    String lineNumber();
    String lineNumbersLeft();
    String lineNumbersRight();
  }

  static {
    Resources.I.diffTableStyle().ensureInjected();
  }

  @UiField Element cm;
  /*@UiField OverviewBar overview;
  @UiField Element patchSetNavRow;
  @UiField Element patchSetNavCellA;
  @UiField Element patchSetNavCellB;
  @UiField Element diffHeaderRow;
  @UiField Element diffHeaderText;
  @UiField FlowPanel widgets;*/
  @UiField static DiffTableStyle style;

 /* @UiField(provided = true)
  PatchSetSelectBox2 patchSetSelectBoxA;

  @UiField(provided = true)
  PatchSetSelectBox2 patchSetSelectBoxB;*/

  UnifiedTable2(Unified2 parent, PatchSet.Id base, PatchSet.Id revision,
      String path) {
    super(parent, base, revision, path);

    /*patchSetSelectBoxA = new PatchSetSelectBox2(
        parent, DisplaySide.A, revision.getParentKey(), base, path);
    patchSetSelectBoxB = new PatchSetSelectBox2(
        parent, DisplaySide.B, revision.getParentKey(), revision, path);
    PatchSetSelectBox2.link(patchSetSelectBoxA, patchSetSelectBoxB);*/

    initWidget(uiBinder.createAndBindUi(this));
  }

  @Override
  void setHideEmptyPane(boolean hide) {
  }
}
