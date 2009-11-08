// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.client.patches;

import com.google.gerrit.client.changes.PatchTable;
import com.google.gerrit.client.changes.Util;
import com.google.gerrit.reviewdb.Patch;
import com.google.gwt.event.logical.shared.ResizeEvent;
import com.google.gwt.event.logical.shared.ResizeHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.PopupPanel.PositionCallback;
import com.google.gwtexpui.globalkey.client.GlobalKey;
import com.google.gwtexpui.globalkey.client.HidePopupPanelCommand;
import com.google.gwtexpui.user.client.PluginSafeDialogBox;

class PatchBrowserPopup extends PluginSafeDialogBox implements
    PositionCallback, ResizeHandler {
  private final Patch.Key callerKey;
  private final PatchTable fileList;
  private final ScrollPanel sp;
  private HandlerRegistration regWindowResize;

  PatchBrowserPopup(final Patch.Key pk, final PatchTable fl) {
    super(true/* autohide */, false/* modal */);

    callerKey = pk;
    fileList = fl;
    sp = new ScrollPanel(fileList);

    final FlowPanel body = new FlowPanel();
    body.setStyleName("gerrit-PatchBrowserPopup-Body");
    body.add(sp);

    setText(Util.M.patchSetHeader(callerKey.getParentKey().get()));
    setWidget(body);
    addStyleName("gerrit-PatchBrowserPopup");
  }

  @Override
  public void setPosition(final int myWidth, int myHeight) {
    final int dLeft = (Window.getClientWidth() - myWidth) >> 1;
    final int cHeight = Window.getClientHeight();
    final int cHeight2 = 2 * cHeight / 3;
    final int sLeft = Window.getScrollLeft();
    final int sTop = Window.getScrollTop();

    if (myHeight > cHeight2) {
      sp.setHeight((cHeight2 - 50) + "px");
      myHeight = getOffsetHeight();
    }
    setPopupPosition(sLeft + dLeft, (sTop + cHeight) - (myHeight + 10));
  }

  @Override
  public void onResize(final ResizeEvent event) {
    sp.setWidth((Window.getClientWidth() - 60) + "px");
    setPosition(getOffsetWidth(), getOffsetHeight());
  }

  @Override
  public void hide() {
    if (regWindowResize != null) {
      regWindowResize.removeHandler();
      regWindowResize = null;
    }
    super.hide();
  }

  @Override
  public void show() {
    super.show();
    if (regWindowResize == null) {
      regWindowResize = Window.addResizeHandler(this);
    }

    GlobalKey.dialog(this);
    GlobalKey.addApplication(this, new HidePopupPanelCommand(0, 'f', this));

    if (!fileList.isLoaded()) {
      fileList.onTableLoaded(new Command() {
        @Override
        public void execute() {
          sp.setHeight("");
          setPosition(getOffsetWidth(), getOffsetHeight());
          fileList.setRegisterKeys(true);
          fileList.movePointerTo(callerKey);
        }
      });
    }
  }

  public void open() {
    if (!fileList.isLoaded()) {
      sp.setHeight("22px");
    }
    sp.setWidth((Window.getClientWidth() - 60) + "px");
    setPopupPositionAndShow(this);
    if (fileList.isLoaded()) {
      fileList.setRegisterKeys(true);
      fileList.movePointerTo(callerKey);
    }
  }
}
