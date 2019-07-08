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

package com.google.gwtexpui.globalkey.client;

import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.user.client.ui.PopupPanel;

/** Hides the given popup panel when invoked. */
public class HidePopupPanelCommand extends KeyCommand {
  private final PopupPanel panel;

  public HidePopupPanelCommand(int mask, int key, PopupPanel panel) {
    super(mask, key, KeyConstants.I.closeCurrentDialog());
    this.panel = panel;
  }

  @Override
  public void onKeyPress(final KeyPressEvent event) {
    panel.hide();
  }
}
