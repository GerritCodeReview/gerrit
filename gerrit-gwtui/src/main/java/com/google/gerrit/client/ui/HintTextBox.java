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

import com.google.gerrit.client.Gerrit;
import com.google.gwt.event.dom.client.BlurEvent;
import com.google.gwt.event.dom.client.BlurHandler;
import com.google.gwt.event.dom.client.FocusEvent;
import com.google.gwt.event.dom.client.FocusHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwtexpui.globalkey.client.NpTextBox;

public class HintTextBox extends NpTextBox {
  private HandlerRegistration hintFocusHandler;
  private HandlerRegistration hintBlurHandler;

  private String hintText;
  private String hintStyleName = Gerrit.RESOURCES.css().inputFieldTypeHint();

  private boolean hintOn;
  private boolean isFocused;

  /*
   * There seems to be a strange bug (at least on firefox 3.5.9 ubuntu) with
   * the textbox under the following circumstances:
   *  1) The field is not focused with BText in it.
   *  2) The field receives focus and a focus listener changes the text to FText
   *  3) The ESC key is pressed and the value of the field has not changed
   *     (ever) from FText
   *  4) BUG: The text value gets reset to BText!
   *
   *  To counter this bug, force all focus events to first force a blur event.
   *  For some reason, this seems to prevent the bug.
   */

  private boolean bugStopping = false;

  public String getText() {
    if (hintOn) {
      return "";
    }
    return super.getText();
  }

  public void setText(String text) {
    focusHint();

    super.setText(text);

    if (! isFocused) {
      blurHint();
    }
  }

  public String getHintText() {
    return hintText;
  }

  public void setHintText(String text) {
    if (text == null) {
      if (hintText == null) { // was not set, still not set, no change.
        return;
      }

      // Clearing a previously set Hint
      hintFocusHandler.removeHandler();
      hintFocusHandler = null;
      hintBlurHandler.removeHandler();
      hintBlurHandler = null;
      hintText = null;
      focusHint();

      return;
    }

    // Setting Hints

    if (hintText == null) { // first time (was not already set)
      hintText = text;

      hintFocusHandler = addFocusHandler(new FocusHandler() {
          @Override
          public void onFocus(FocusEvent event) {
            setFocus(true);
          }
        });

      hintBlurHandler = addBlurHandler(new BlurHandler() {
          @Override
          public void onBlur(BlurEvent event) {
            setFocus(false);
          }
        });

    } else { // Changing an already set Hint

      focusHint();
      hintText = text;
    }

    if (! isFocused) {
      blurHint();
    }
  }

  public void setHintStyleName(String styleName) {
    if (hintStyleName != null && hintOn) {
      removeStyleName(hintStyleName);
    }

    hintStyleName = styleName;

    if (styleName != null && hintOn) {
      addStyleName(styleName);
    }
  }

  public String getHintStyleName() {
    return hintStyleName;
  }

  protected void blurHint() {
    if (! hintOn && getHintText() != null && "".equals(super.getText())) {
      hintOn = true;
      super.setText(getHintText());
      if (getHintStyleName() != null) {
        addStyleName(getHintStyleName());
      }
    }
  }

  protected void focusHint() {
    if (hintOn) {
      super.setText("");
      if (getHintStyleName() != null) {
        removeStyleName(getHintStyleName());
      }
      hintOn = false;
    }
  }

  public void setFocus(boolean focus) {
    if (focus) {
      bugStopping = true;
      super.setFocus(!focus);
      bugStopping = false;
    }

    super.setFocus(focus);

    if (focus) {
      focusHint();
    } else if (!bugStopping) {
      blurHint();
    }

    isFocused = focus;
  }
}
