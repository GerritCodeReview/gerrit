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

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.changes.ChangeInfo.RevisionInfo;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.dom.client.Element;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Composite;
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
    String activeLine();
    String range();
    String rangeHighlight();
    String showtabs();
  }

  @UiField
  Element cmA;

  @UiField
  Element cmB;

  @UiField
  SidePanel sidePanel;

  @UiField
  Element patchSetNavRow;

  @UiField
  Element patchSetNavCellA;

  @UiField
  Element patchSetNavCellB;

  @UiField(provided = true)
  PatchSetSelectBox2 patchSetSelectBoxA;

  @UiField(provided = true)
  PatchSetSelectBox2 patchSetSelectBoxB;

  @UiField
  Element fileCommentRow;

  @UiField
  Element fileCommentCellA;

  @UiField
  Element fileCommentCellB;

  @UiField(provided = true)
  FileCommentPanel fileCommentPanelA;

  @UiField(provided = true)
  FileCommentPanel fileCommentPanelB;

  @UiField
  static DiffTableStyle style;

  private SideBySide2 host;

  DiffTable(SideBySide2 host, PatchSet.Id base, PatchSet.Id revision, String path) {
    patchSetSelectBoxA = new PatchSetSelectBox2(
        this, DisplaySide.A, revision.getParentKey(), base, path);
    patchSetSelectBoxB = new PatchSetSelectBox2(
        this, DisplaySide.B, revision.getParentKey(), revision, path);
    PatchSetSelectBox2.link(patchSetSelectBoxA, patchSetSelectBoxB);
    fileCommentPanelA = new FileCommentPanel(host, this, path, DisplaySide.A);
    fileCommentPanelB = new FileCommentPanel(host, this, path, DisplaySide.B);
    initWidget(uiBinder.createAndBindUi(this));
    this.host = host;
  }

  void setHeaderVisible(boolean show) {
    Gerrit.setHeaderVisible(show);
    UIObject.setVisible(patchSetNavRow, show);
    UIObject.setVisible(fileCommentRow, show
        && (fileCommentPanelA.getBoxCount() > 0
            || fileCommentPanelB.getBoxCount() > 0));
    if (show) {
      host.header.removeStyleName(style.fullscreen());
    } else {
      host.header.addStyleName(style.fullscreen());
    }
    host.resizeCodeMirror();
  }

  private FileCommentPanel getPanelFromSide(DisplaySide side) {
    return side == DisplaySide.A ? fileCommentPanelA : fileCommentPanelB;
  }

  void createOrEditFileComment(DisplaySide side) {
    getPanelFromSide(side).createOrEditFileComment();
    setHeaderVisible(true);
  }

  void addFileCommentBox(CommentBox box) {
    getPanelFromSide(box.getSide()).addFileComment(box);
  }

  void onRemoveDraftBox(DraftBox box) {
    getPanelFromSide(box.getSide()).onRemoveDraftBox(box);
  }

  int getHeaderHeight() {
    return fileCommentRow.getOffsetHeight() + patchSetSelectBoxA.getOffsetHeight();
  }

  void setUpPatchSetNav(JsArray<RevisionInfo> list, DiffInfo info) {
    patchSetSelectBoxA.setUpPatchSetNav(list, info.meta_a());
    patchSetSelectBoxB.setUpPatchSetNav(list, info.meta_b());
  }

  void add(Widget widget) {
    ((HTMLPanel) getWidget()).add(widget);
  }
}
