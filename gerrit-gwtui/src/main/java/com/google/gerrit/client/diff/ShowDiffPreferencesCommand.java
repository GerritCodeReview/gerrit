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

import com.google.gerrit.client.patches.PatchUtil;
import com.google.gerrit.client.ui.ListenableAccountDiffPreference;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.logical.shared.CloseEvent;
import com.google.gwt.event.logical.shared.CloseHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.PopupPanel.PositionCallback;
import com.google.gwtexpui.globalkey.client.KeyCommand;

public class ShowDiffPreferencesCommand extends KeyCommand {
  private DiffPreferencesPopup current;
  private ListenableAccountDiffPreference prefs;

  ShowDiffPreferencesCommand(
      ListenableAccountDiffPreference prefs) {
    super(0, 's', PatchUtil.C.showSettings());
    this.prefs = prefs;
  }

  @Override
  public void onKeyPress(final KeyPressEvent event) {
    if (current != null) {
      // Already open? Close the dialog.
      current.hide();
      current = null;
      return;
    }

    final DiffPreferencesPopup settings = new DiffPreferencesPopup(prefs);
    settings.addCloseHandler(new CloseHandler<PopupPanel>() {
      @Override
      public void onClose(final CloseEvent<PopupPanel> event) {
        current = null;
      }
    });
    current = settings;
    settings.setPopupPositionAndShow(new PositionCallback() {
      @Override
      public void setPosition(final int pWidth, final int pHeight) {
        final int left = (int)(Window.getClientWidth() * 0.02);
        final int wLeft = Window.getScrollLeft();
        final int wTop = Window.getScrollTop();
        settings.setPopupPosition(wLeft + left, wTop + 150);
      }
    });
  }
}
