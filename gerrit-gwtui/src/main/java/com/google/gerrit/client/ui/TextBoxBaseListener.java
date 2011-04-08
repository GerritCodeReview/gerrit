// Copyright (C) 2011 The Android Open Source Project
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

import com.google.gwt.event.dom.client.FocusEvent;
import com.google.gwt.event.dom.client.FocusHandler;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.event.dom.client.MouseUpEvent;
import com.google.gwt.event.dom.client.MouseUpHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.DeferredCommand;
import com.google.gwt.user.client.ui.TextBoxBase;


/** Listen To all text changes to a TextBoxBase. */
public class TextBoxBaseListener implements KeyPressHandler, KeyDownHandler,
   MouseUpHandler {

  private String old = "";


  public TextBoxBaseListener(final TextBoxBase tb) {
    old = tb.getText();
    tb.addKeyPressHandler(this);

    // Is there another way to capture middle button X11 pastes in browsers
    // which do not yet support ONPASTE events (Firefox)?
    tb.addMouseUpHandler(this);

    // Resetting the "original text" on focus ensures that we are
    // up to date with non-user updates of the text (calls to
    // setText()...) and also up to date with user changes which
    // occured after enabling "widget".
    tb.addFocusHandler(new FocusHandler() {
        @Override
        public void onFocus(FocusEvent event) {
          old = tb.getText();
        }
      });

    // CTRL-V Pastes in Chrome seem only detectable via BrowserEvents or
    // KeyDownEvents, the latter is better.
    tb.addKeyDownHandler(this);
  }

  // Handlers

  @Override
  public void onKeyPress(final KeyPressEvent e) {
    on(e);
  }

  @Override
  public void onKeyDown(final KeyDownEvent e) {
    on(e);
  }

  @Override
  public void onMouseUp(final MouseUpEvent e) {
    on(e);
  }

  private void on(final GwtEvent e) {
    onTextBoxBase((TextBoxBase) e.getSource());
  }

  private void onTextBoxBase(final TextBoxBase tb) {
    if (! tb.getText().equals(old)) {
      onTextChange(old, tb.getText());
      old = tb.getText();
    } else {

      // The text appears to not always get updated until the handlers complete.
      DeferredCommand.add(new Command() {
        @Override
        public void execute() {
          if (! tb.getText().equals(old)) {
            onTextChange(old, tb.getText());
            old = tb.getText();
          }
        }
      });
    }
  }

  public void onTextChange(String old, String val) {
  }
}