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

import com.google.gerrit.client.DiffObject;
import com.google.gerrit.client.account.DiffPreferences;
import com.google.gerrit.client.info.ChangeInfo.RevisionInfo;
import com.google.gerrit.reviewdb.client.Patch.ChangeType;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.UIObject;
import com.google.gwt.user.client.ui.Widget;
import net.codemirror.lib.CodeMirror;

/** Base class for SideBySideTable2 and UnifiedTable2 */
abstract class DiffTable extends Composite {
  static {
    Resources.I.diffTableStyle().ensureInjected();
  }

  interface Style extends CssResource {
    String fullscreen();

    String dark();

    String noIntraline();

    String range();

    String rangeHighlight();

    String diffHeader();

    String showLineNumbers();
  }

  @UiField Element patchSetNavRow;
  @UiField Element patchSetNavCellA;
  @UiField Element patchSetNavCellB;
  @UiField Element diffHeaderRow;
  @UiField Element diffHeaderText;
  @UiField FlowPanel widgets;

  @UiField(provided = true)
  PatchSetSelectBox patchSetSelectBoxA;

  @UiField(provided = true)
  PatchSetSelectBox patchSetSelectBoxB;

  private boolean header;
  private ChangeType changeType;
  Scrollbar scrollbar;

  DiffTable(DiffScreen parent, DiffObject base, DiffObject revision, String path) {
    patchSetSelectBoxA =
        new PatchSetSelectBox(
            parent,
            DisplaySide.A,
            parent.getProject(),
            revision.asPatchSetId().getParentKey(),
            base,
            path);
    patchSetSelectBoxB =
        new PatchSetSelectBox(
            parent,
            DisplaySide.B,
            parent.getProject(),
            revision.asPatchSetId().getParentKey(),
            revision,
            path);
    PatchSetSelectBox.link(patchSetSelectBoxA, patchSetSelectBoxB);

    this.scrollbar = new Scrollbar(this);
  }

  abstract boolean isVisibleA();

  void setHeaderVisible(boolean show) {
    DiffScreen parent = getDiffScreen();
    if (show != UIObject.isVisible(patchSetNavRow)) {
      UIObject.setVisible(patchSetNavRow, show);
      UIObject.setVisible(diffHeaderRow, show && header);
      if (show) {
        parent.header.removeStyleName(Resources.I.diffTableStyle().fullscreen());
      } else {
        parent.header.addStyleName(Resources.I.diffTableStyle().fullscreen());
      }
      parent.resizeCodeMirror();
    }
  }

  abstract int getHeaderHeight();

  ChangeType getChangeType() {
    return changeType;
  }

  void setUpBlameIconA(CodeMirror cm, boolean isBase, PatchSet.Id rev, String path) {
    patchSetSelectBoxA.setUpBlame(cm, isBase, rev, path);
  }

  void setUpBlameIconB(CodeMirror cm, PatchSet.Id rev, String path) {
    patchSetSelectBoxB.setUpBlame(cm, false, rev, path);
  }

  void set(
      DiffPreferences prefs,
      JsArray<RevisionInfo> list,
      int parents,
      DiffInfo info,
      boolean editExists,
      boolean current,
      boolean open,
      boolean binary) {
    this.changeType = info.changeType();
    patchSetSelectBoxA.setUpPatchSetNav(
        list, parents, info.metaA(), editExists, current, open, binary);
    patchSetSelectBoxB.setUpPatchSetNav(
        list, parents, info.metaB(), editExists, current, open, binary);

    JsArrayString hdr = info.diffHeader();
    if (hdr != null) {
      StringBuilder b = new StringBuilder();
      for (int i = 1; i < hdr.length(); i++) {
        String s = hdr.get(i);
        if (!info.binary()
            && (s.startsWith("diff --git ")
                || s.startsWith("index ")
                || s.startsWith("+++ ")
                || s.startsWith("--- "))) {
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
    if (header) {
      CodeMirror cm = getDiffScreen().getCmFromSide(DisplaySide.A);
      diffHeaderText.getStyle().setMarginLeft(cm.getGutterElement().getOffsetWidth(), Unit.PX);
    }
  }

  void add(Widget widget) {
    widgets.add(widget);
  }

  abstract DiffScreen getDiffScreen();

  boolean hasHeader() {
    return header;
  }
}
