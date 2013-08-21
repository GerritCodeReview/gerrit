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

import com.google.gerrit.client.account.DiffPreferences;
import com.google.gerrit.client.changes.ChangeInfo.RevisionInfo;
import com.google.gerrit.reviewdb.client.Patch.ChangeType;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.UIObject;
import com.google.gwt.user.client.ui.Widget;

import net.codemirror.lib.CodeMirror;

/**
 * Base class for SideBySideTable2 and UnifiedTable2
 */
abstract class DiffTable extends Composite {

  /*interface DiffTableStyle extends CssResource {
    String fullscreen();
    //String intralineBg();
    String dark();
    //String diff();
    String noIntraline();
    String activeLine();
    //String range();
    //String rangeHighlight();
    String showTabs();
    String showLineNumbers();
    //String columnMargin();
  }*/

  static {
    Resources.I.diffTableStyle().ensureInjected();
  }

  @UiField OverviewBar overview;
  @UiField Element patchSetNavRow;
  @UiField Element patchSetNavCellA;
  @UiField Element patchSetNavCellB;
  @UiField Element diffHeaderRow;
  @UiField Element diffHeaderText;
  @UiField FlowPanel widgets;
  //@UiField static DiffTableStyle style;

  @UiField(provided = true)
  PatchSetSelectBox2 patchSetSelectBoxA;

  @UiField(provided = true)
  PatchSetSelectBox2 patchSetSelectBoxB;

  private DiffScreen parent;
  private boolean header;
  private boolean headerVisible;
  private boolean visibleA;
  private ChangeType changeType;

  DiffTable(DiffScreen parent, PatchSet.Id base, PatchSet.Id revision,
      String path) {
    patchSetSelectBoxA = new PatchSetSelectBox2(
        parent, DisplaySide.A, revision.getParentKey(), base, path);
    patchSetSelectBoxB = new PatchSetSelectBox2(
        parent, DisplaySide.B, revision.getParentKey(), revision, path);
    PatchSetSelectBox2.link(patchSetSelectBoxA, patchSetSelectBoxB);

    this.parent = parent;
    this.headerVisible = true;
    this.visibleA = true;
  }

  boolean isVisibleA() {
    return visibleA;
  }

  boolean isHeaderVisible() {
    return headerVisible;
  }

  void setHeaderVisible(boolean show) {
    headerVisible = show;
    UIObject.setVisible(patchSetNavRow, show);
    UIObject.setVisible(diffHeaderRow, show && header);
    if (show) {
      parent.header.removeStyleName(Resources.I.diffTableStyle().fullscreen());
    } else {
      parent.header.addStyleName(Resources.I.diffTableStyle().fullscreen());
    }
    parent.resizeCodeMirror();
  }

  int getHeaderHeight() {
    int h = patchSetSelectBoxA.getOffsetHeight();
    if (header) {
      h += diffHeaderRow.getOffsetHeight();
    }
    return h;
  }

  ChangeType getChangeType() {
    return changeType;
  }

  void set(DiffPreferences prefs, JsArray<RevisionInfo> list, DiffInfo info) {
    this.changeType = info.change_type();
    patchSetSelectBoxA.setUpPatchSetNav(list, info.meta_a());
    patchSetSelectBoxB.setUpPatchSetNav(list, info.meta_b());

    JsArrayString hdr = info.diff_header();
    if (hdr != null) {
      StringBuilder b = new StringBuilder();
      for (int i = 1; i < hdr.length(); i++) {
        String s = hdr.get(i);
        if (s.startsWith("diff --git ")
            || s.startsWith("index ")
            || s.startsWith("+++ ")
            || s.startsWith("--- ")) {
          continue;
        }
        b.append(s).append('\n');
      }

      String hdrTxt = b.toString().trim();
      header = !hdrTxt.isEmpty();
      diffHeaderText.setInnerText(hdrTxt);
      UIObject.setVisible(diffHeaderRow, header);
    } else {
      header = false;
      UIObject.setVisible(diffHeaderRow, false);
    }
    setHideEmptyPane(prefs.hideEmptyPane());
  }

  abstract void setHideEmptyPane(boolean hide);

  void refresh() {
    overview.refresh();
    if (header) {
      CodeMirror cm = parent.getCmFromSide(DisplaySide.A);
      diffHeaderText.getStyle().setMarginLeft(
          cm.getGutterElement().getOffsetWidth(),
          Unit.PX);
    }
  }

  void add(Widget widget) {
    widgets.add(widget);
  }

  DiffScreen getDiffScreen() {
    return parent;
  }
}
