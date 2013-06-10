//Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.client.diff;

import com.google.gerrit.client.AvatarImage;
import com.google.gerrit.client.FormatUtil;
import com.google.gerrit.client.account.AccountInfo;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.Widget;

import java.sql.Timestamp;

/** An HtmlPanel holding the DialogBox to display a comment */
class CommentBox extends Composite {
  interface Binder extends UiBinder<HTMLPanel, CommentBox> {}
  private static Binder uiBinder = GWT.create(Binder.class);

  interface CommentBoxStyle extends CssResource {
    String open();
    String close();
  }

  private HandlerRegistration headerClick;
  private Runnable clickCallback;

  @UiField
  Widget header;

  @UiField
  AvatarImage avatar;

  @UiField
  Element name;

  @UiField
  Element summary;

  @UiField
  Element date;

  @UiField
  Element contentPanel;

  @UiField
  Element contentPanelMessage;

  @UiField
  CommentBoxStyle style;

  CommentBox(AccountInfo author, Timestamp when, String message,
      boolean isDraft) {
    initWidget(uiBinder.createAndBindUi(this));
    // TODO: Format the comment box differently based on whether isDraft
    // is true.
    setAuthorNameText(author);
    date.setInnerText(FormatUtil.shortFormatDayTime(when));
    setMessageText(message);
    setOpen(false);
  }

  @Override
  protected void onLoad() {
    super.onLoad();

    headerClick = header.addDomHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        setOpen(!isOpen());
        if (clickCallback != null) {
          clickCallback.run();
        }
      }
    }, ClickEvent.getType());
  }

  void setOpenCloseHandler(final Runnable callback) {
    clickCallback = callback;
  }

  @Override
  protected void onUnload() {
    super.onUnload();

    if (headerClick != null) {
      headerClick.removeHandler();
      headerClick = null;
    }
  }

  private void setAuthorNameText(AccountInfo author) {
    // TODO: Set avatar's display to none if we get a 404.
    avatar = new AvatarImage(author, 26);
    name.setInnerText(FormatUtil.name(author));
  }

  private void setMessageText(String message) {
    if (message == null) {
      message = "";
    } else {
      message = message.trim();
    }
    summary.setInnerText(message);
    // TODO: Change to use setInnerHtml
    contentPanelMessage.setInnerText(message);
  }

  private void setOpen(boolean open) {
    if (open) {
      removeStyleName(style.close());
      addStyleName(style.open());
    } else {
      removeStyleName(style.open());
      addStyleName(style.close());
    }
  }

  private boolean isOpen() {
    return getStyleName().contains(style.open());
  }
}
