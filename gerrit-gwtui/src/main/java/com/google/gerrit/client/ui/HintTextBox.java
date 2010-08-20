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

import com.google.gwt.event.dom.client.BlurEvent;
import com.google.gwt.event.dom.client.BlurHandler;
import com.google.gwt.event.dom.client.FocusEvent;
import com.google.gwt.event.dom.client.FocusHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwtexpui.globalkey.client.NpTextBox;

public class HintTextBox extends NpTextBox {
  private String hintText;
  private String hintStyleName;
  private boolean hintOn;
  private HandlerRegistration hintFocusHandler;
  private HandlerRegistration hintBlurHandler;

  public String getText() {
    if (hintOn) {
      return "";
    }
    return super.getText();
  }

  public void setText(String text) {
    focusHint();
    super.setText(text);
    blurHint();
  }

  public String getHintText() {
    return hintText;
  }

  public void setHintText(String text) {
    if (text == null) {

      // Clearing Hints

      if (hintText != null) { // was set
        hintFocusHandler.removeHandler();
        hintFocusHandler = null;
        hintBlurHandler.removeHandler();
        hintBlurHandler = null;
        hintText = null;
        focusHint();
      }
      return;
    }

    // Setting Hints

    if (hintText == null) { // first time (was not already set)
      hintText = text;

      hintFocusHandler = addFocusHandler(new FocusHandler() {
          @Override
          public void onFocus(FocusEvent event) {
            focusHint();
          }
        });

      hintBlurHandler = addBlurHandler(new BlurHandler() {
          @Override
          public void onBlur(BlurEvent event) {
            blurHint();
          }
        });

      blurHint();
      return;
    }

    // Changing Hint

    hintText = text;
    if (hintOn) {
      super.setText(text);
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
    if ("".equals(super.getText()) && ! hintOn) {
      super.setText(getHintText());
      hintOn = true;
      if (getHintStyleName() != null) {
        addStyleName(getHintStyleName());
      }
    }
  }

  protected void focusHint() {
    if (hintOn) {
      super.setText("");
      hintOn = false;
      if (getHintStyleName() != null) {
        removeStyleName(getHintStyleName());
      }
    }
  }
}
