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

package com.google.gerrit.client.api;

import com.google.gerrit.client.actions.ActionButton;
import com.google.gerrit.client.change.Resources;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.logical.shared.CloseEvent;
import com.google.gwt.event.logical.shared.CloseHandler;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwtexpui.globalkey.client.GlobalKey;

class PopupHelper {
  static PopupHelper popup(ActionContext ctx, Element panel) {
    PopupHelper helper = new PopupHelper(ctx.button(), panel);
    helper.show();
    ctx.button().link(ctx);
    return helper;
  }

  private final ActionButton activatingButton;
  private final FlowPanel panel;
  private PopupPanel popup;

  PopupHelper(ActionButton button, Element child) {
    activatingButton = button;
    panel = new FlowPanel();
    panel.setStyleName(Resources.I.style().popupContent());
    panel.getElement().appendChild(child);
  }

  void show() {
    final PopupPanel p = new PopupPanel(true);
    p.setStyleName(Resources.I.style().popup());
    p.addAutoHidePartner(activatingButton.getElement());
    p.addCloseHandler(
        new CloseHandler<PopupPanel>() {
          @Override
          public void onClose(CloseEvent<PopupPanel> event) {
            activatingButton.unlink();
            if (popup == p) {
              popup = null;
            }
          }
        });
    p.add(panel);
    p.showRelativeTo(activatingButton);
    GlobalKey.dialog(p);
    popup = p;
  }

  void hide() {
    if (popup != null) {
      activatingButton.unlink();
      popup.hide();
      popup = null;
    }
  }
}
