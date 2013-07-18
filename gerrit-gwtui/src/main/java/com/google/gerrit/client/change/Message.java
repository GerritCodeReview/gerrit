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

import com.google.gerrit.client.AvatarImage;
import com.google.gerrit.client.FormatUtil;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.changes.ChangeInfo.MessageInfo;
import com.google.gerrit.client.changes.Util;
import com.google.gerrit.client.ui.CommentLinkProcessor;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.UIObject;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;

class Message extends Composite {
  interface Binder extends UiBinder<HTMLPanel, Message> {}
  private static Binder uiBinder = GWT.create(Binder.class);

  static interface Style extends CssResource {
    String closed();
  }

  @UiField Style style;
  @UiField Element name;
  @UiField Element summary;
  @UiField Element date;
  @UiField Element message;

  @UiField(provided = true)
  AvatarImage avatar;

  Message(CommentLinkProcessor clp, MessageInfo info) {
    if (info.author() != null) {
      avatar = new AvatarImage(info.author(), 26);
      avatar.setSize("", "");
    } else {
      avatar = new AvatarImage();
    }

    initWidget(uiBinder.createAndBindUi(this));
    addDomHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        setOpen(!isOpen());
      }
    }, ClickEvent.getType());

    name.setInnerText(authorName(info));
    date.setInnerText(FormatUtil.shortFormatDayTime(info.date()));
    if (info.message() != null) {
      String msg = info.message().trim();
      summary.setInnerText(msg);
      message.setInnerSafeHtml(clp.apply(
          new SafeHtmlBuilder().append(msg).wikify()));
    }
  }

  private boolean isOpen() {
    return UIObject.isVisible(message);
  }

  private void setOpen(boolean open) {
    UIObject.setVisible(summary, !open);
    UIObject.setVisible(message, open);
    if (open) {
      removeStyleName(style.closed());
    } else {
      addStyleName(style.closed());
    }
  }

  static String authorName(MessageInfo info) {
    if (info.author() != null) {
      if (info.author().name() != null) {
        return info.author().name();
      }
      return Gerrit.getConfig().getAnonymousCowardName();
    }
    return Util.C.messageNoAuthor();
  }
}
