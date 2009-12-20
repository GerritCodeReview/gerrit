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

package com.google.gerrit.client;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwtexpui.user.client.AutoCenterDialogBox;

/** A dialog box telling the user they are not signed in. */
public class NotSignedInDialog extends AutoCenterDialogBox {
  public NotSignedInDialog() {
    super(/* auto hide */true, /* modal */true);
    setText(Gerrit.C.notSignedInTitle());

    final FlowPanel buttons = new FlowPanel();
    buttons.setStyleName(Gerrit.RESOURCES.css().errorDialogButtons());

    final Button signin = new Button();
    signin.setText(Gerrit.C.menuSignIn());
    signin.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        hide();
        Gerrit.doSignIn();
      }
    });
    buttons.add(signin);

    final Button close = new Button();
    close.setText(Gerrit.C.errorDialogClose());
    close.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        hide();
      }
    });
    buttons.add(close);

    final FlowPanel center = new FlowPanel();
    center.setStyleName(Gerrit.RESOURCES.css().errorDialog());
    center.add(new HTML(Gerrit.C.notSignedInBody()));
    center.add(buttons);
    add(center);

    setWidth("40em");
  }
}
