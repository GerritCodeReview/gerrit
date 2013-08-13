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

import com.google.gerrit.client.diff.SideBySide2.DisplaySide;
import com.google.gwt.core.client.GWT;
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
  private static Binder uiBinder = GWT.create(Binder.class);

  interface DiffTableStyle extends CssResource {
    String intralineBg();
    String diff();
    String padding();
    String activeLine();
    String activeLineBg();
    String hideNumber();
  }

  @UiField
  Element cmA;

  @UiField
  Element cmB;

  @UiField
  SidePanel sidePanel;

  @UiField
  Element patchsetNavRow;

  @UiField
  Element patchsetNavCellA;

  @UiField
  Element patchsetNavCellB;

  @UiField(provided = true)
  PatchSelectBox2 patchSelectBoxA;

  @UiField(provided = true)
  PatchSelectBox2 patchSelectBoxB;

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

  DiffTable(SideBySide2 host, String path) {
    patchSelectBoxA = new PatchSelectBox2(this, DisplaySide.A);
    patchSelectBoxB = new PatchSelectBox2(this, DisplaySide.B);
    fileCommentPanelA = new FileCommentPanel(host, this, path, DisplaySide.A);
    fileCommentPanelB = new FileCommentPanel(host, this, path, DisplaySide.B);
    initWidget(uiBinder.createAndBindUi(this));
    this.host = host;
  }

  @Override
  protected void onLoad() {
    updateFileCommentVisibility(false);
  }

  void updateFileCommentVisibility(boolean forceHide) {
    UIObject.setVisible(patchsetNavRow, !forceHide);
    if (forceHide || (fileCommentPanelA.getBoxCount() == 0 &&
        fileCommentPanelB.getBoxCount() == 0)) {
      UIObject.setVisible(fileCommentRow, false);
    } else {
      UIObject.setVisible(fileCommentRow, true);
    }
    host.resizeCodeMirror();
  }

  private FileCommentPanel getPanelFromSide(DisplaySide side) {
    return side == DisplaySide.A ? fileCommentPanelA : fileCommentPanelB;
  }

  void createOrEditFileComment(DisplaySide side) {
    getPanelFromSide(side).createOrEditFileComment();
    updateFileCommentVisibility(false);
  }

  void addFileCommentBox(CommentBox box) {
    getPanelFromSide(box.getSide()).addFileComment(box);
  }

  void onRemoveDraftBox(DraftBox box) {
    getPanelFromSide(box.getSide()).onRemoveDraftBox(box);
  }

  int getHeaderHeight() {
    return fileCommentRow.getOffsetHeight() + patchSelectBoxA.getOffsetHeight();
  }

  void add(Widget widget) {
    ((HTMLPanel) getWidget()).add(widget);
  }
}
