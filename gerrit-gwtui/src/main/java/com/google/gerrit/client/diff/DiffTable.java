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
import com.google.gerrit.client.info.ChangeInfo.RevisionInfo;
import com.google.gerrit.reviewdb.client.Patch.ChangeType;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.UIObject;
import com.google.gwt.user.client.ui.Widget;

import net.codemirror.lib.CodeMirror;

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
    String dark();
    String diff();
    String noIntraline();
    String range();
    String rangeHighlight();
    String showLineNumbers();
    String hideA();
    String hideB();
    String padding();
  }

  @UiField Element cmA;
  @UiField Element cmB;
  Scrollbar scrollbar;
  @UiField Element patchSetNavRow;
  @UiField Element patchSetNavCellA;
  @UiField Element patchSetNavCellB;
  @UiField Element diffHeaderRow;
  @UiField Element diffHeaderText;
  @UiField FlowPanel widgets;
  @UiField static DiffTableStyle style;

  @UiField(provided = true)
  PatchSetSelectBox patchSetSelectBoxA;

  @UiField(provided = true)
  PatchSetSelectBox patchSetSelectBoxB;

  private SideBySide parent;
  private boolean header;
  private boolean visibleA;
  private ChangeType changeType;

  DiffTable(SideBySide parent, PatchSet.Id base, PatchSet.Id revision,
      String path) {
    patchSetSelectBoxA = new PatchSetSelectBox(
        parent, DisplaySide.A, revision.getParentKey(), base, path);
    patchSetSelectBoxB = new PatchSetSelectBox(
        parent, DisplaySide.B, revision.getParentKey(), revision, path);
    PatchSetSelectBox.link(patchSetSelectBoxA, patchSetSelectBoxB);

    initWidget(uiBinder.createAndBindUi(this));
    this.scrollbar = new Scrollbar(this);
    this.parent = parent;
    this.visibleA = true;
  }

  boolean isVisibleA() {
    return visibleA;
  }

  void setVisibleA(boolean show) {
    visibleA = show;
    if (show) {
      removeStyleName(style.hideA());
      parent.syncScroll(DisplaySide.B); // match B's viewport
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
      parent.syncScroll(DisplaySide.A); // match A's viewport
    } else {
      addStyleName(style.hideB());
    }
  }

  void setHeaderVisible(boolean show) {
    if (show != UIObject.isVisible(patchSetNavRow)) {
      UIObject.setVisible(patchSetNavRow, show);
      UIObject.setVisible(diffHeaderRow, show && header);
      if (show) {
        parent.header.removeStyleName(style.fullscreen());
      } else {
        parent.header.addStyleName(style.fullscreen());
      }
      parent.resizeCodeMirror();
    }
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

  void set(DiffPreferences prefs, JsArray<RevisionInfo> list, DiffInfo info, ClickHandler onBlameA, ClickHandler onBlameB,
      boolean editExists, boolean current, boolean open, boolean binary) {
    this.changeType = info.changeType();
    patchSetSelectBoxA.setUpPatchSetNav(list, info.metaA(), onBlameA, editExists,
        current, open, binary);
    patchSetSelectBoxB.setUpPatchSetNav(list, info.metaB(), onBlameB, editExists,
        current, open, binary);

    JsArrayString hdr = info.diffHeader();
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

  void setHideEmptyPane(boolean hide) {
    if (changeType == ChangeType.ADDED) {
      setVisibleA(!hide);
    } else if (changeType == ChangeType.DELETED) {
      setVisibleB(!hide);
    }
  }

  void refresh() {
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
}
