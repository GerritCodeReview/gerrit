// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.client.change;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.logical.shared.CloseEvent;
import com.google.gwt.event.logical.shared.CloseHandler;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwtexpui.globalkey.client.GlobalKey;
import com.google.gwtexpui.globalkey.client.NpTextArea;

abstract class ActionMessageBox extends Composite {
  interface Binder extends UiBinder<HTMLPanel, ActionMessageBox> {}

  private static final Binder uiBinder = GWT.create(Binder.class);

  interface Style extends CssResource {
    String popup();
  }

  private final Button activatingButton;
  private PopupPanel popup;

  @UiField Style style;
  @UiField NpTextArea message;
  @UiField Button send;

  ActionMessageBox(Button button) {
    this.activatingButton = button;
    initWidget(uiBinder.createAndBindUi(this));
    send.setText(button.getText());
  }

  abstract void send(String message);

  void show() {
    if (popup != null) {
      popup.hide();
      popup = null;
      return;
    }

    final PopupPanel p = new PopupPanel(true);
    p.setStyleName(style.popup());
    p.addAutoHidePartner(activatingButton.getElement());
    p.addCloseHandler(
        new CloseHandler<PopupPanel>() {
          @Override
          public void onClose(CloseEvent<PopupPanel> event) {
            if (popup == p) {
              popup = null;
            }
          }
        });
    p.add(this);
    p.showRelativeTo(activatingButton);
    GlobalKey.dialog(p);
    message.setFocus(true);
    popup = p;
  }

  void hide() {
    if (popup != null) {
      popup.hide();
      popup = null;
    }
  }

  @UiHandler("message")
  void onMessageKey(KeyPressEvent event) {
    if ((event.getCharCode() == '\n' || event.getCharCode() == KeyCodes.KEY_ENTER)
        && event.isControlKeyDown()) {
      event.preventDefault();
      event.stopPropagation();
      onSend(null);
    }
  }

  @UiHandler("send")
  void onSend(@SuppressWarnings("unused") ClickEvent e) {
    send(message.getValue().trim());
  }
}
