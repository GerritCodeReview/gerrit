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
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.dom.client.ParagraphElement;
import com.google.gwt.dom.client.SpanElement;
import com.google.gwt.dom.client.TableCellElement;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.UIObject;

import java.sql.Timestamp;

/** An HtmlPanel holding the DialogBox to display a comment */
class CommentBox extends Composite {
  interface Binder extends UiBinder<HTMLPanel, CommentBox> {}
  private static Binder uiBinder = GWT.create(Binder.class);

  private static final int SUMMARY_LENGTH = 75;

  interface CommentBoxStyle extends CssResource {
    String commentBox();
  }

  @UiField
  HTMLPanel header;

  @UiField
  TableCellElement avatar;

  @UiField
  TableCellElement name;

  @UiField
  SpanElement summary;

  @UiField
  TableCellElement date;

  @UiField
  DivElement contentPanel;

  @UiField
  ParagraphElement contentPanelMessage;

  @UiField
  CommentBoxStyle style;

  CommentBox(AccountInfo author, Timestamp when, String message, boolean isDraft) {
    initWidget(uiBinder.createAndBindUi(this));
    setAuthorNameText(author, FormatUtil.name(author));
    date.setInnerText(FormatUtil.shortFormatDayTime(when));
    setMessageText(message);
    setOpen(false);
  }

  void addClickHandler(final Runnable callback) {
    header.addDomHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        setOpen(!isOpen());
        callback.run();
      }
    }, ClickEvent.getType());
  }

  private void setAuthorNameText(AccountInfo author, String nameText) {
    avatar.appendChild(new AvatarImage(author, 26).getElement());
    name.setInnerText(nameText);
  }

  private void setMessageText(String message) {
    if (message == null) {
      message = "";
    } else {
      message = message.trim();
    }
    summary.setInnerText(summarize(message));
    contentPanelMessage.setInnerText(message);
  }

  private static String summarize(String message) {
    if (message.length() < SUMMARY_LENGTH) {
      return message;
    }
    int p = 0;
    StringBuilder r = new StringBuilder();
    while (r.length() < SUMMARY_LENGTH) {
      int e = message.indexOf(' ', p);
      if (e < 0) {
        break;
      }
      String word = message.substring(p, e).trim();
      if (SUMMARY_LENGTH <= r.length() + word.length() + 1) {
        break;
      }
      if (r.length() > 0) {
        r.append(' ');
      }
      r.append(word);
      p = e + 1;
    }
    r.append(" \u2026"); // Ellipsis
    return r.toString();
  }

  private void setOpen(boolean open) {
    UIObject.setVisible(summary, !open);
    UIObject.setVisible(contentPanel, open);
  }

  private boolean isOpen() {
    return UIObject.isVisible(contentPanel);
  }
}
