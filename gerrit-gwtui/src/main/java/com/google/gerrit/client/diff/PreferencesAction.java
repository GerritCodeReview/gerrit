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
import com.google.gwt.event.logical.shared.CloseEvent;
import com.google.gwt.event.logical.shared.CloseHandler;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.PopupPanel.PositionCallback;
import com.google.gwt.user.client.ui.Widget;

class PreferencesAction {
  private final DiffScreen view;
  private final DiffPreferences prefs;
  private PopupPanel popup;
  private PreferencesBox current;
  private Widget partner;

  PreferencesAction(DiffScreen view, DiffPreferences prefs) {
    this.view = view;
    this.prefs = prefs;
  }

  void update() {
    if (current != null) {
      current.set(prefs);
    }
  }

  void show() {
    if (popup != null) {
      // Already open? Close the dialog.
      hide();
      return;
    }

    current = new PreferencesBox(view);
    current.set(prefs);

    popup = new PopupPanel(true, false);
    popup.setStyleName(current.style.dialog());
    popup.add(current);
    popup.addAutoHidePartner(partner.getElement());
    popup.addCloseHandler(
        new CloseHandler<PopupPanel>() {
          @Override
          public void onClose(CloseEvent<PopupPanel> event) {
            view.getCmFromSide(DisplaySide.B).focus();
            popup = null;
            current = null;
          }
        });
    popup.setPopupPositionAndShow(
        new PositionCallback() {
          @Override
          public void setPosition(int offsetWidth, int offsetHeight) {
            popup.setPopupPosition(300, 120);
          }
        });
    current.setFocus(true);
  }

  void hide() {
    if (popup != null) {
      popup.hide();
      popup = null;
      current = null;
    }
  }

  void setPartner(Widget w) {
    partner = w;
  }
}
