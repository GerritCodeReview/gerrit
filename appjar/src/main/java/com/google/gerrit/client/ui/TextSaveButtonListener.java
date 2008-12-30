// Copyright 2008 Google Inc.
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

import com.google.gwt.user.client.ui.FocusWidget;
import com.google.gwt.user.client.ui.KeyboardListenerAdapter;
import com.google.gwt.user.client.ui.TextBoxBase;
import com.google.gwt.user.client.ui.Widget;

/** Enables an action (e.g. a Button) if the text box is modified. */
public class TextSaveButtonListener extends KeyboardListenerAdapter {
  private final TextBoxBase descText;
  private final FocusWidget descAction;

  public TextSaveButtonListener(final TextBoxBase text, final FocusWidget action) {
    descText = text;
    descAction = action;

    descText.addKeyboardListener(this);
  }

  @Override
  public void onKeyPress(final Widget sender, final char key, final int mod) {
    if (mod == 0) {
      switch (key) {
        case KEY_UP:
        case KEY_DOWN:
        case KEY_LEFT:
        case KEY_RIGHT:
        case KEY_HOME:
        case KEY_END:
        case KEY_PAGEUP:
        case KEY_PAGEDOWN:
        case KEY_ALT:
        case KEY_CTRL:
        case KEY_SHIFT:
          break;
        default:
          descAction.setEnabled(descText.isEnabled());
          break;
      }
    }
  }
}
