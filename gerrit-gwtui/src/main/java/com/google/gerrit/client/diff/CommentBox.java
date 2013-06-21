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

import com.google.gerrit.client.account.AccountInfo;
import com.google.gerrit.client.ui.CommentLinkProcessor;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwtexpui.safehtml.client.SafeHtml;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;

import java.sql.Timestamp;

/** An HtmlPanel for displaying a comment */
abstract class CommentBox extends Composite {

  interface CommentBoxStyle extends CssResource {
    String open();
    String close();
  }

  private CommentLinkProcessor commentLinkProcessor;
  private HandlerRegistration headerClick;
  private Runnable clickCallback;

  @UiField
  CommentBoxHeader header;

  @UiField
  HTML contentPanelMessage;

  @UiField
  CommentBoxStyle style;

  protected void init(AccountInfo author, Timestamp when, String message,
      CommentLinkProcessor linkProcessor, boolean isDraft) {
    commentLinkProcessor = linkProcessor;
    header.setNameAndDate(author, when, isDraft);
    setMessageText(message);
    setOpen(false);
    setClickHandler();
  }

  private void setClickHandler() {
    if (headerClick == null) {
      header.addDomHandler(new ClickHandler() {
        @Override
        public void onClick(ClickEvent event) {
          setOpen(!isOpen());
          runClickCallback();
        }
      }, ClickEvent.getType());
    };
  }

  void setOpenCloseHandler(final Runnable callback) {
    clickCallback = callback;
  }

  protected void runClickCallback() {
    if (clickCallback != null) {
      clickCallback.run();
    }
  }

  @Override
  public void onUnload() {
    super.onUnload();

    if (headerClick != null) {
      headerClick.removeHandler();
      headerClick = null;
    }
  }

  private void setMessageText(String message) {
    if (message == null) {
      message = "";
    } else {
      message = message.trim();
    }
    header.setSummaryText(message);
    SafeHtml buf = new SafeHtmlBuilder().append(message).wikify();
    buf = commentLinkProcessor.apply(buf);
    SafeHtml.set(contentPanelMessage, buf);
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
