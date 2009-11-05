// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.client.ui;

import com.google.gerrit.client.FormatUtil;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.data.AccountInfo;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.DoubleClickEvent;
import com.google.gwt.event.dom.client.DoubleClickHandler;
import com.google.gwt.event.dom.client.HasDoubleClickHandlers;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;
import com.google.gwtexpui.safehtml.client.SafeHtml;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;

import java.util.Date;

public class CommentPanel extends Composite implements HasDoubleClickHandlers {
  private static final int SUMMARY_LENGTH = 75;
  private final FlexTable header;
  private final InlineLabel messageSummary;
  private final FlowPanel content;
  private final DoubleClickHTML messageText;
  private FlowPanel buttons;
  private boolean recent;

  public CommentPanel(final AccountInfo author, final Date when, String message) {
    this();

    setMessageText(message);
    setAuthorNameText(FormatUtil.name(author));
    setDateText(FormatUtil.shortFormat(when));

    final CellFormatter fmt = header.getCellFormatter();
    fmt.getElement(0, 0).setTitle(FormatUtil.nameEmail(author));
    fmt.getElement(0, 2).setTitle(FormatUtil.mediumFormat(when));
  }

  protected CommentPanel() {
    final FlowPanel body = new FlowPanel();
    initWidget(body);
    setStyleName("gerrit-CommentPanel");

    messageSummary = new InlineLabel();
    messageSummary.setStyleName("gerrit-CommentPanel-Summary");

    header = new FlexTable();
    header.setStyleName("gerrit-CommentPanel-Header");
    header.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        setOpen(!isOpen());
      }
    });
    header.setText(0, 0, "");
    header.setWidget(0, 1, messageSummary);
    header.setText(0, 2, "");
    final CellFormatter fmt = header.getCellFormatter();
    fmt.setStyleName(0, 0, "gerrit-CommentPanel-AuthorCell");
    fmt.setStyleName(0, 1, "gerrit-CommentPanel-SummaryCell");
    fmt.setStyleName(0, 2, "gerrit-CommentPanel-DateCell");
    fmt.setHorizontalAlignment(0, 2, HasHorizontalAlignment.ALIGN_RIGHT);
    body.add(header);

    content = new FlowPanel();
    content.setStyleName("gerrit-CommentPanel-Content");
    content.setVisible(false);
    body.add(content);

    messageText = new DoubleClickHTML();
    messageText.setStyleName("gerrit-CommentPanel-Message");
    content.add(messageText);
  }

  @Override
  public HandlerRegistration addDoubleClickHandler(DoubleClickHandler handler) {
    return messageText.addDoubleClickHandler(handler);
  }

  protected void setMessageText(String message) {
    if (message == null) {
      message = "";
    } else {
      message = message.trim();
    }

    messageSummary.setText(summarize(message));
    SafeHtml.set(messageText, new SafeHtmlBuilder().append(message).wikify()
        .replaceAll(Gerrit.getConfig().getCommentLinks()));
  }

  public void setAuthorNameText(final String nameText) {
    header.setText(0, 0, nameText);
  }

  protected void setDateText(final String dateText) {
    header.setText(0, 2, dateText);
  }

  protected void setMessageTextVisible(final boolean show) {
    messageText.setVisible(show);
  }

  protected void addContent(final Widget w) {
    if (buttons != null) {
      content.insert(w, content.getWidgetIndex(buttons));
    } else {
      content.add(w);
    }
  }

  protected Panel getButtonPanel() {
    if (buttons == null) {
      buttons = new FlowPanel();
      buttons.setStyleName("gerrit-CommentPanel-Buttons");
      content.add(buttons);
    }
    return buttons;
  }

  private static String summarize(final String message) {
    if (message.length() < SUMMARY_LENGTH) {
      return message;
    }

    int p = 0;
    final StringBuilder r = new StringBuilder();
    while (r.length() < SUMMARY_LENGTH) {
      final int e = message.indexOf(' ', p);
      if (e < 0) {
        break;
      }

      final String word = message.substring(p, e).trim();
      if (SUMMARY_LENGTH <= r.length() + word.length() + 1) {
        break;
      }
      if (r.length() > 0) {
        r.append(' ');
      }
      r.append(word);
      p = e + 1;
    }
    r.append(" \u2026");
    return r.toString();
  }

  public boolean isOpen() {
    return content.isVisible();
  }

  public void setOpen(final boolean open) {
    messageSummary.setVisible(!open);
    content.setVisible(open);
  }

  public boolean isRecent() {
    return recent;
  }

  public void setRecent(final boolean r) {
    recent = r;
  }

  private static class DoubleClickHTML extends HTML implements
      HasDoubleClickHandlers {
    public HandlerRegistration addDoubleClickHandler(DoubleClickHandler handler) {
      return addDomHandler(handler, DoubleClickEvent.getType());
    }
  }
}
