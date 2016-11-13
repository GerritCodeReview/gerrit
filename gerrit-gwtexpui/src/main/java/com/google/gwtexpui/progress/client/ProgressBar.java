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

package com.google.gwtexpui.progress.client;

import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;

/**
 * A simple progress bar with a text label.
 *
 * <p>The bar is 200 pixels wide and 20 pixels high. To keep the implementation simple and
 * lightweight this dimensions are fixed and shouldn't be modified by style overrides in client code
 * or CSS.
 */
public class ProgressBar extends Composite {
  static {
    ProgressResources.I.css().ensureInjected();
  }

  private final String callerText;
  private final Label bar;
  private final Label msg;
  private int value;

  /** Create a bar with no message text. */
  public ProgressBar() {
    this("");
  }

  /** Create a bar displaying the specified message. */
  public ProgressBar(final String text) {
    if (text == null || text.length() == 0) {
      callerText = "";
    } else {
      callerText = text + " ";
    }

    final FlowPanel body = new FlowPanel();
    body.setStyleName(ProgressResources.I.css().container());

    msg = new Label(callerText);
    msg.setStyleName(ProgressResources.I.css().text());
    body.add(msg);

    bar = new Label("");
    bar.setStyleName(ProgressResources.I.css().bar());
    body.add(bar);

    initWidget(body);
  }

  /** @return the current value of the progress meter. */
  public int getValue() {
    return value;
  }

  /** Update the bar's percent completion. */
  public void setValue(final int pComplete) {
    assert 0 <= pComplete && pComplete <= 100;
    value = pComplete;
    bar.setWidth(2 * pComplete + "px");
    msg.setText(callerText + pComplete + "%");
  }
}
