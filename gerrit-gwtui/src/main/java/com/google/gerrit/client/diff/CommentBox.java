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
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Widget;
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

  @UiField(provided=true)
  CommentBoxHeader header;

  @UiField
  HTML contentPanelMessage;

  @UiField
  CommentBoxResources res;

  protected CommentBox(UiBinder<? extends Widget, CommentBox> binder,
      AccountInfo author, Timestamp when, String message,
      CommentLinkProcessor linkProcessor, boolean isDraft) {
    commentLinkProcessor = linkProcessor;
    header = new CommentBoxHeader(author, when, isDraft);
    initWidget(binder.createAndBindUi(this));
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
    res.style().ensureInjected();
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
  protected void onUnload() {
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
      removeStyleName(res.style().close());
      addStyleName(res.style().open());
    } else {
      removeStyleName(res.style().open());
      addStyleName(res.style().close());
    }
  }

  private boolean isOpen() {
    return getStyleName().contains(res.style().open());
  }
}
