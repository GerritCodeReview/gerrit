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

package com.google.gerrit.client;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwtexpui.globalkey.client.GlobalKey;
import com.google.gwtexpui.user.client.AutoCenterDialogBox;

public class ConfirmationDialog extends AutoCenterDialogBox {


  private Button cancelButton;

  public ConfirmationDialog(final String dialogTitle, final HTML message,
      final ConfirmationCallback callback) {
    super(/* auto hide */false, /* modal */true);
    setGlassEnabled(true);
    setText(dialogTitle);

    final FlowPanel buttons = new FlowPanel();

    final Button okButton = new Button();
    okButton.setText(Gerrit.C.confirmationDialogOk());
    okButton.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        hide();
        callback.onOk();
      }
    });
    buttons.add(okButton);

    cancelButton = new Button();
    DOM.setStyleAttribute(cancelButton.getElement(), "marginLeft", "300px");
    cancelButton.setText(Gerrit.C.confirmationDialogCancel());
    cancelButton.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        hide();
      }
    });
    buttons.add(cancelButton);

    final FlowPanel center = new FlowPanel();
    center.add(message);
    center.add(buttons);
    add(center);

    message.setWidth("400px");

    setWidget(center);
  }

  @Override
  public void center() {
    super.center();
    GlobalKey.dialog(this);
    cancelButton.setFocus(true);
  }
}
