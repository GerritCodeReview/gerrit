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

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Style;
import com.google.gwt.event.logical.shared.CloseEvent;
import com.google.gwt.event.logical.shared.CloseHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.UIObject;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtexpui.globalkey.client.GlobalKey;

abstract class RightSidePopdownAction {
  private final ChangeScreen.Style style;
  private final Widget button;
  private final UIObject relativeTo;
  private PopupPanel popup;

  RightSidePopdownAction(ChangeScreen.Style style, UIObject relativeTo, Widget button) {
    this.style = style;
    this.relativeTo = relativeTo;
    this.button = button;
  }

  abstract Widget getWidget();

  void show() {
    if (popup != null) {
      button.removeStyleName(style.selected());
      popup.hide();
      return;
    }

    final PopupPanel p =
        new PopupPanel(true) {
          @Override
          public void setPopupPosition(int left, int top) {
            top -= Document.get().getBodyOffsetTop();

            int w = Window.getScrollLeft() + Window.getClientWidth();
            int r = relativeTo.getAbsoluteLeft() + relativeTo.getOffsetWidth();
            int right = w - r;
            Style style = getElement().getStyle();
            style.clearProperty("left");
            style.setPropertyPx("right", right);
            style.setPropertyPx("top", top);
          }
        };
    p.setStyleName(style.replyBox());
    p.addAutoHidePartner(button.getElement());
    p.addCloseHandler(
        new CloseHandler<PopupPanel>() {
          @Override
          public void onClose(CloseEvent<PopupPanel> event) {
            if (popup == p) {
              button.removeStyleName(style.selected());
              popup = null;
            }
          }
        });
    p.add(getWidget());
    p.showRelativeTo(relativeTo);
    GlobalKey.dialog(p);
    button.addStyleName(style.selected());
    popup = p;
  }
}
