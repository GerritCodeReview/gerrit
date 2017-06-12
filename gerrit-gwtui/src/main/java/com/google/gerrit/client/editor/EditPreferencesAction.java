// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.client.editor;

import com.google.gerrit.client.account.EditPreferences;
import com.google.gwt.event.logical.shared.CloseEvent;
import com.google.gwt.event.logical.shared.CloseHandler;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.PopupPanel.PositionCallback;

class EditPreferencesAction {
  private final EditScreen view;
  private final EditPreferences prefs;
  private PopupPanel popup;
  private EditPreferencesBox current;

  EditPreferencesAction(EditScreen view, EditPreferences prefs) {
    this.view = view;
    this.prefs = prefs;
  }

  void show() {
    if (popup != null) {
      hide();
      return;
    }

    current = new EditPreferencesBox(view);
    current.set(prefs);

    popup = new PopupPanel(true, false);
    popup.setStyleName(current.style.dialog());
    popup.add(current);
    popup.addCloseHandler(
        new CloseHandler<PopupPanel>() {
          @Override
          public void onClose(CloseEvent<PopupPanel> event) {
            view.getEditor().focus();
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
  }

  void hide() {
    if (popup != null) {
      popup.hide();
      popup = null;
      current = null;
    }
  }
}
