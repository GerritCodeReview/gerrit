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

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.changes.ChangeInfo.MessageInfo;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.logical.shared.ResizeEvent;
import com.google.gwt.event.logical.shared.ResizeHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.Window.ScrollEvent;
import com.google.gwt.user.client.Window.ScrollHandler;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.RootPanel;

import java.sql.Timestamp;
import java.util.HashSet;
import java.util.List;

/** Displays the "New Message From ..." panel in bottom right on updates. */
abstract class UpdateAvailableBar extends PopupPanel {
  interface Binder extends UiBinder<HTMLPanel, UpdateAvailableBar> {}
  private static Binder uiBinder = GWT.create(Binder.class);

  static interface Style extends CssResource {
    String popup();
  }

  private Timestamp updated;
  private HandlerRegistration resizer;
  private HandlerRegistration scroller;

  @UiField Style style;
  @UiField Element author;
  @UiField Anchor show;
  @UiField Anchor ignore;

  UpdateAvailableBar() {
    super(/* autoHide = */ false, /* modal = */ false);
    add(uiBinder.createAndBindUi(this));
    setStyleName(style.popup());
  }

  void set(List<MessageInfo> newMessages, Timestamp newTime) {
    HashSet<Integer> seen = new HashSet<Integer>();
    StringBuilder r = new StringBuilder();
    for (MessageInfo m : newMessages) {
      int a = m.author() != null ? m.author()._account_id() : 0;
      if (seen.add(a)) {
        if (r.length() > 0) {
          r.append(", ");
        }
        r.append(Message.authorName(m));
      }
    }
    author.setInnerText(r.toString());
    updated = newTime;

    if (isShowing()) {
      setPopupPosition(
          Window.getScrollLeft() + Window.getClientWidth() - getOffsetWidth(),
          Window.getScrollTop() + Window.getClientHeight() - getOffsetHeight());
    }
  }

  void popup() {
    setPopupPositionAndShow(new PositionCallback() {
      @Override
      public void setPosition(int w, int h) {
        w += 7; // Initial information is wrong, adjust with some slop.
        h += 19;
        setPopupPosition(
            Window.getScrollLeft() + Window.getClientWidth() - w,
            Window.getScrollTop() + Window.getClientHeight() - h);
      }
    });
    if (resizer == null) {
      resizer = Window.addResizeHandler(new ResizeHandler() {
        @Override
        public void onResize(ResizeEvent event) {
          setPopupPosition(
              Window.getScrollLeft() + event.getWidth() - getOffsetWidth(),
              Window.getScrollTop() + event.getHeight() - getOffsetHeight());
        }
      });
    }
    if (scroller == null) {
      scroller = Window.addWindowScrollHandler(new ScrollHandler() {
        @Override
        public void onWindowScroll(ScrollEvent event) {
          RootPanel b = Gerrit.getBottomMenu();
          int br = b.getAbsoluteLeft() + b.getOffsetWidth();
          int bp = b.getAbsoluteTop() + b.getOffsetHeight();
          int wr = event.getScrollLeft() + Window.getClientWidth();
          int wp = event.getScrollTop() + Window.getClientHeight();
          setPopupPosition(
              Math.min(br, wr) - getOffsetWidth(),
              Math.min(bp, wp) - getOffsetHeight());
        }
      });
    }
  }

  @Override
  public void hide() {
    if (resizer != null) {
      resizer.removeHandler();
      resizer = null;
    }
    if (scroller != null) {
      scroller.removeHandler();
      scroller = null;
    }
    super.hide();
  }

  @UiHandler("show")
  void onShow(ClickEvent e) {
    onShow();
  }

  @UiHandler("ignore")
  void onIgnore(ClickEvent e) {
    onIgnore(updated);
    hide();
  }

  abstract void onShow();
  abstract void onIgnore(Timestamp newTime);
}
