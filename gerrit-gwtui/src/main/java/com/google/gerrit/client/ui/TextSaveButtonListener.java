// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.client.ui;

import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.user.client.ui.FocusWidget;
import com.google.gwt.user.client.ui.TextBoxBase;

/** Enables an action (e.g. a Button) if the text box is modified. */
public class TextSaveButtonListener implements KeyPressHandler {
  private final FocusWidget descAction;

  public TextSaveButtonListener(final FocusWidget action) {
    descAction = action;
  }

  public TextSaveButtonListener(final TextBoxBase text, final FocusWidget action) {
    this(action);
    text.addKeyPressHandler(this);
  }

  @Override
  public void onKeyPress(final KeyPressEvent e) {
    if (descAction.isEnabled()) {
      // Do nothing, its already enabled.
    } else if (e.isControlKeyDown() || e.isAltKeyDown() || e.isMetaKeyDown()) {
      switch (e.getCharCode()) {
        case 'v':
        case 'x':
          on(e);
          break;
      }
    } else {
      switch (e.getCharCode()) {
        case KeyCodes.KEY_UP:
        case KeyCodes.KEY_DOWN:
        case KeyCodes.KEY_LEFT:
        case KeyCodes.KEY_RIGHT:
        case KeyCodes.KEY_HOME:
        case KeyCodes.KEY_END:
        case KeyCodes.KEY_PAGEUP:
        case KeyCodes.KEY_PAGEDOWN:
        case KeyCodes.KEY_ALT:
        case KeyCodes.KEY_CTRL:
        case KeyCodes.KEY_SHIFT:
          break;
        default:
          on(e);
          break;
      }
    }
  }

  private void on(final KeyPressEvent e) {
    descAction.setEnabled(((TextBoxBase) e.getSource()).isEnabled());
  }
}
