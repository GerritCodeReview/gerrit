// Copyright 2009 Google Inc.
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

import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;

public class ProgressMeter extends Composite {
  private final String callerText;
  private final Label bar;
  private final Label msg;

  public ProgressMeter() {
    this("");
  }

  public ProgressMeter(final String text) {
    if (text == null || text.length() == 0) {
      callerText = "";
    } else {
      callerText = text + " ";
    }

    final FlowPanel body = new FlowPanel();
    body.setStyleName("gerrit-ProgressMeter");

    msg = new Label(callerText);
    msg.setStyleName("gerrit-ProgressMeterText");
    body.add(msg);

    bar = new Label("");
    bar.setStyleName("gerrit-ProgressMeterBar");
    body.add(bar);

    initWidget(body);
  }

  public void setValue(final int v) {
    bar.setWidth("" + (2 * v) + "px");
    msg.setText(callerText + v + "%");
  }
}
