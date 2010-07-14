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
import com.google.gwt.event.logical.shared.CloseEvent;
import com.google.gwt.event.logical.shared.CloseHandler;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwtexpui.globalkey.client.GlobalKey;
import com.google.gwtexpui.user.client.AutoCenterDialogBox;

/** A dialog box telling the user they are not signed in. */
public class NotSignedInDialog extends AutoCenterDialogBox implements CloseHandler<PopupPanel> {

  private Button signin;
  private boolean buttonClicked = false;

  public NotSignedInDialog() {
    super(/* auto hide */false, /* modal */true);
    setGlassEnabled(true);
    setText(Gerrit.C.notSignedInTitle());

    final FlowPanel buttons = new FlowPanel();
    signin = new Button();
    signin.setText(Gerrit.C.menuSignIn());
    signin.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        buttonClicked = true;
        hide();
        Gerrit.doSignIn(History.getToken());
      }
    });
    buttons.add(signin);

    final Button close = new Button();
    DOM.setStyleAttribute(close.getElement(), "marginLeft", "200px");
    close.setText(Gerrit.C.signInDialogClose());
    close.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        buttonClicked = true;
        Gerrit.deleteSessionCookie();
        hide();
      }
    });
    buttons.add(close);

    final FlowPanel center = new FlowPanel();
    center.add(new HTML(Gerrit.C.notSignedInBody()));
    center.add(buttons);
    add(center);

    center.setWidth("400px");

    addCloseHandler(this);
  }

  @Override
  public void onClose(CloseEvent<PopupPanel> event) {
    if (!buttonClicked) {
      // the dialog was closed without one of the buttons being pressed
      // e.g. the user pressed ESC to close the dialog
      Gerrit.deleteSessionCookie();
    }
  }

  @Override
  public void center() {
    super.center();
    GlobalKey.dialog(this);
    signin.setFocus(true);
  }
}
