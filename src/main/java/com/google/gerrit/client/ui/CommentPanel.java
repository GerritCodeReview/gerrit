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
import com.google.gerrit.client.data.AccountInfo;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;

import java.util.Date;

public class CommentPanel extends Composite {
  private static final int SUMMARY_LENGTH = 75;
  private final Widget summary;
  private final Widget content;
  private boolean recent;

  public CommentPanel(final AccountInfo author, final Date when, String message) {
    message = message.trim();

    final FlowPanel body = new FlowPanel();
    initWidget(body);
    setStyleName("gerrit-CommentPanel");

    summary = new InlineLabel(summarize(message));
    summary.setStyleName("gerrit-CommentPanel-Summary");

    final FlexTable header = new FlexTable();
    header.setStyleName("gerrit-CommentPanel-Header");
    header.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        setOpen(!isOpen());
      }
    });
    header.setText(0, 0, FormatUtil.name(author));
    header.setWidget(0, 1, summary);
    header.setText(0, 2, FormatUtil.shortFormat(when));
    final CellFormatter fmt = header.getCellFormatter();
    fmt.setStyleName(0, 0, "gerrit-CommentPanel-AuthorCell");
    fmt.setStyleName(0, 1, "gerrit-CommentPanel-SummaryCell");
    fmt.setStyleName(0, 2, "gerrit-CommentPanel-DateCell");
    fmt.setHorizontalAlignment(0, 2, HasHorizontalAlignment.ALIGN_RIGHT);
    fmt.getElement(0, 0).setTitle(FormatUtil.nameEmail(author));
    fmt.getElement(0, 2).setTitle(FormatUtil.mediumFormat(when));
    body.add(header);

    content = new SafeHtmlBuilder().append(message).wikify().toBlockWidget();
    content.setStyleName("gerrit-CommentPanel-Message");
    content.setVisible(false);
    body.add(content);
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
    summary.setVisible(!open);
    content.setVisible(open);
  }

  public boolean isRecent() {
    return recent;
  }

  public void setRecent(final boolean r) {
    recent = r;
  }
}
