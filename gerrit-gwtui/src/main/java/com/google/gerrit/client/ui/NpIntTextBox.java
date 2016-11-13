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

import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.event.dom.client.KeyEvent;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwtexpui.globalkey.client.NpTextBox;

/** Text box that accepts only integer values. */
public class NpIntTextBox extends NpTextBox {
  private int intValue;

  public NpIntTextBox() {
    init();
  }

  private void init() {
    addKeyDownHandler(
        new KeyDownHandler() {
          @Override
          public void onKeyDown(KeyDownEvent event) {
            int code = event.getNativeKeyCode();
            onKey(event, code, code);
          }
        });
    addKeyPressHandler(
        new KeyPressHandler() {
          @Override
          public void onKeyPress(KeyPressEvent event) {
            int charCode = event.getCharCode();
            int keyCode = event.getNativeEvent().getKeyCode();
            onKey(event, charCode, keyCode);
          }
        });
  }

  private void onKey(KeyEvent<?> event, int charCode, int keyCode) {
    if ('0' <= charCode && charCode <= '9') {
      if (event.isAnyModifierKeyDown()) {
        event.preventDefault();
      }
    } else {
      switch (keyCode) {
        case KeyCodes.KEY_BACKSPACE:
        case KeyCodes.KEY_LEFT:
        case KeyCodes.KEY_RIGHT:
        case KeyCodes.KEY_HOME:
        case KeyCodes.KEY_END:
        case KeyCodes.KEY_TAB:
        case KeyCodes.KEY_DELETE:
          break;

        default:
          // Allow copy and paste using ctl-c/ctrl-v,
          // or whatever the platform's convention is.
          if (!(event.isControlKeyDown() || event.isMetaKeyDown() || event.isAltKeyDown())) {
            event.preventDefault();
          }
          break;
      }
    }
  }

  public int getIntValue() {
    String txt = getText().trim();
    if (!txt.isEmpty()) {
      try {
        intValue = Integer.parseInt(getText());
      } catch (NumberFormatException e) {
        // Ignored
      }
    }
    return intValue;
  }

  public void setIntValue(int v) {
    intValue = v;
    setText(Integer.toString(v));
  }
}
