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

import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.PopupPanel;

/** Customized PopupPanel widget to represent a ToolTip. */
public class ToolTip extends PopupPanel {

  private static final int DEFAULT_X = 100;
  private static final int DEFAULT_Y = -10;

  /**
   * Constructor
   *
   * @param text ToolTip text.
   * @param leftPos ToolTip left position on screen.
   * @param topPos ToolTip top position on screen.
   */
  public ToolTip(String text, int leftPos, int topPos) {

    super(true);

    int left = leftPos + DEFAULT_X;
    int top = topPos + DEFAULT_Y;

    this.setPopupPosition(left, top);
    this.add(new Label(text));
  }

  public void showToolTip() {
    this.show();
  }

  @Override
  public boolean onEventPreview(Event event) {
    int type = DOM.eventGetType(event);
    switch (type) {
      case Event.ONMOUSEOUT:
      case Event.ONCLICK: {
        this.hide();
        return true;
      }
    }
    return false;
  }
}
