// Copyright (C) 2010 The Android Open Source Project
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

import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwtexpui.globalkey.client.NpTextBox;

/** Text box that accepts only integer values. */
public class NpIntTextBox extends NpTextBox {
  private int intValue;

  public NpIntTextBox() {
    init();
  }

  public NpIntTextBox(Element element) {
    super(element);
    init();
  }

  private void init() {
    addKeyPressHandler(new KeyPressHandler() {
      @Override
      public void onKeyPress(KeyPressEvent event) {
        char c = event.getCharCode();
        if (c < '0' || '9' < c) {
          final int nativeCode = event.getNativeEvent().getKeyCode();
          switch (nativeCode) {
            case KeyCodes.KEY_BACKSPACE:
            case KeyCodes.KEY_LEFT:
            case KeyCodes.KEY_RIGHT:
            case KeyCodes.KEY_HOME:
            case KeyCodes.KEY_END:
            case KeyCodes.KEY_TAB:
            case KeyCodes.KEY_DELETE:
              break;

            default:
              if (!event.isAnyModifierKeyDown()) {
                event.preventDefault();
              }
              break;
          }
        }
      }
    });
  }

  public int getIntValue() {
    String txt = getText().trim();
    if (!txt.isEmpty()) {
      try {
        intValue = Integer.parseInt(getText());
      } catch (NumberFormatException e) {
      }
    }
    return intValue;
  }

  public void setIntValue(int v) {
    intValue = v;
    setText(Integer.toString(v));
  }
}
