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
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtexpui.globalkey.client.GlobalKey;
import com.google.gwtexpui.safehtml.client.SafeHtml;
import com.google.gwtexpui.user.client.AutoCenterDialogBox;

public class ConfirmationDialog extends AutoCenterDialogBox {

  private Button cancelButton;
  private Button okButton;

  public ConfirmationDialog(
      final String dialogTitle, SafeHtml message, ConfirmationCallback callback) {
    super(/* auto hide */ false, /* modal */ true);
    setGlassEnabled(true);
    setText(dialogTitle);

    final FlowPanel buttons = new FlowPanel();

    okButton = new Button();
    okButton.setText(Gerrit.C.confirmationDialogOk());
    okButton.addClickHandler(
        new ClickHandler() {
          @Override
          public void onClick(ClickEvent event) {
            hide();
            callback.onOk();
          }
        });
    buttons.add(okButton);

    cancelButton = new Button();
    cancelButton.getElement().getStyle().setProperty("marginLeft", "300px");
    cancelButton.setText(Gerrit.C.confirmationDialogCancel());
    cancelButton.addClickHandler(
        new ClickHandler() {
          @Override
          public void onClick(ClickEvent event) {
            hide();
            callback.onCancel();
          }
        });
    buttons.add(cancelButton);

    final FlowPanel center = new FlowPanel();
    final Widget msgWidget = message.toBlockWidget();
    center.add(msgWidget);
    center.add(buttons);
    add(center);

    msgWidget.setWidth("400px");

    setWidget(center);
  }

  @Override
  public void center() {
    super.center();
    GlobalKey.dialog(this);
    cancelButton.setFocus(true);
  }

  public void setCancelVisible(boolean visible) {
    cancelButton.setVisible(visible);
    if (!visible) {
      okButton.setFocus(true);
    }
  }
}
