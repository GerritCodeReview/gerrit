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

import com.google.gerrit.reviewdb.client.Patch.ChangeType;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.HTMLPanel;

/**
 * A table with one row and two columns to hold the two CodeMirrors displaying
 * the files to be compared.
 */
class SideBySideTable2 extends DiffTable {
  interface Binder extends UiBinder<HTMLPanel, SideBySideTable2> {}
  private static final Binder uiBinder = GWT.create(Binder.class);

  interface DiffTableStyle extends CssResource {
    String fullscreen();
    String intralineBg();
    String dark();
    String diff();
    String noIntraline();
    String activeLine();
    String range();
    String rangeHighlight();
    String showTabs();
    String showLineNumbers();
    String hideA();
    String hideB();
    String columnMargin();
    String padding();
  }

  static {
    Resources.I.diffTableStyle().ensureInjected();
  }

  @UiField Element cmA;
  @UiField Element cmB;
  /*@UiField OverviewBar overview;
  @UiField Element patchSetNavRow;
  @UiField Element patchSetNavCellA;
  @UiField Element patchSetNavCellB;
  @UiField Element diffHeaderRow;
  @UiField Element diffHeaderText;
  @UiField FlowPanel widgets;*/
  @UiField static DiffTableStyle style;

  /*@UiField(provided = true)
  PatchSetSelectBox2 patchSetSelectBoxA;

  @UiField(provided = true)
  PatchSetSelectBox2 patchSetSelectBoxB;*/

  private boolean visibleA;

  SideBySideTable2(SideBySide2 parent, PatchSet.Id base, PatchSet.Id revision,
      String path) {
    super(parent, base, revision, path);

    /*patchSetSelectBoxA = new PatchSetSelectBox2(
        parent, DisplaySide.A, revision.getParentKey(), base, path);
    patchSetSelectBoxB = new PatchSetSelectBox2(
        parent, DisplaySide.B, revision.getParentKey(), revision, path);
    PatchSetSelectBox2.link(patchSetSelectBoxA, patchSetSelectBoxB);*/

    initWidget(uiBinder.createAndBindUi(this));
    this.visibleA = true;
  }

  boolean isVisibleA() {
    return visibleA;
  }

  void setVisibleA(boolean show) {
    visibleA = show;
    if (show) {
      removeStyleName(style.hideA());
      ((SideBySide2) getDiffScreen()).syncScroll(DisplaySide.B); // match B's viewport
    } else {
      addStyleName(style.hideA());
    }
  }

  Runnable toggleA() {
    return new Runnable() {
      @Override
      public void run() {
        setVisibleA(!isVisibleA());
      }
    };
  }

  void setVisibleB(boolean show) {
    if (show) {
      removeStyleName(style.hideB());
      ((SideBySide2) getDiffScreen()).syncScroll(DisplaySide.A); // match A's viewport
    } else {
      addStyleName(style.hideB());
    }
  }

  @Override
  void setHideEmptyPane(boolean hide) {
    if (getChangeType() == ChangeType.ADDED) {
      setVisibleA(!hide);
    } else if (getChangeType() == ChangeType.DELETED) {
      setVisibleB(!hide);
    }
  }
}
