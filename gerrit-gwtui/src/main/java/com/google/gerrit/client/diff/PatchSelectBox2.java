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
import com.google.gerrit.client.patches.PatchUtil;
import com.google.gerrit.common.changes.Side;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.Image;

/**
 * HTMLPanel to select among patches
 * TODO: Implement this.
 */
class PatchSelectBox2 extends Composite {
  interface Binder extends UiBinder<HTMLPanel, PatchSelectBox2> {}
  private static Binder uiBinder = GWT.create(Binder.class);

  @UiField(provided = true)
  Image icon;

  @UiField
  Anchor hide;

  private DiffTable table;
  private Side side;
  private boolean visible;
  private PatchSelectBox2 other;

  PatchSelectBox2(DiffTable table, Side side) {
    icon = new Image(Gerrit.RESOURCES.addFileComment());
    icon.setTitle(PatchUtil.C.addFileCommentToolTip());
    icon.addStyleName(Gerrit.RESOURCES.css().link());
    initWidget(uiBinder.createAndBindUi(this));
    this.table = table;
    this.side = side;
  }

  static void link(PatchSelectBox2 boxA, PatchSelectBox2 boxB) {
    boxA.other = boxB;
    boxB.other = boxA;
  }

  void setVisibilityText(boolean isVisible) {
    if (isVisible) {
      hide.setText("Hide file comment"); // TODO: i18n
      visible = true;
    } else {
      hide.setText("Show file comment");
      visible = false;
    }
  }

  void toggleVisible(boolean visible) {
    setVisibilityText(visible);
    other.setVisibilityText(visible);
  }

  @UiHandler("icon")
  void onIconClick(ClickEvent e) {
    table.createOrEditFileComment(side);
  }

  @UiHandler("hide")
  void onHideClick(ClickEvent e) {
    table.updateVisibility(visible);
    toggleVisible(!visible);
  }
}
