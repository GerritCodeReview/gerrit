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

import com.google.gerrit.client.AvatarImage;
import com.google.gerrit.client.FormatUtil;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.account.AccountInfo;
import com.google.gwt.event.dom.client.BlurEvent;
import com.google.gwt.event.dom.client.BlurHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.DoubleClickEvent;
import com.google.gwt.event.dom.client.DoubleClickHandler;
import com.google.gwt.event.dom.client.FocusEvent;
import com.google.gwt.event.dom.client.FocusHandler;
import com.google.gwt.event.dom.client.HasBlurHandlers;
import com.google.gwt.event.dom.client.HasDoubleClickHandlers;
import com.google.gwt.event.dom.client.HasFocusHandlers;
import com.google.gwt.event.shared.HandlerManager;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtexpui.safehtml.client.SafeHtml;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;

import java.util.Date;

public class CommentPanel extends Composite implements HasDoubleClickHandlers,
    HasFocusHandlers, FocusHandler, HasBlurHandlers, BlurHandler {
  private static final int SUMMARY_LENGTH = 75;
  private final HandlerManager handlerManager = new HandlerManager(this);
  private final FlexTable header;
  private final InlineLabel messageSummary;
  private final FlowPanel content;
  private final DoubleClickHTML messageText;
  private CommentLinkProcessor commentLinkProcessor;
  private FlowPanel buttons;
  private boolean recent;

  public CommentPanel(final AccountInfo author, final Date when, String message,
      CommentLinkProcessor commentLinkProcessor) {
    this(commentLinkProcessor);

    setMessageText(message);
    setAuthorNameText(author.email(), FormatUtil.name(author));
    setDateText(FormatUtil.shortFormatDayTime(when));

    final CellFormatter fmt = header.getCellFormatter();
    fmt.getElement(0, 1).setTitle(FormatUtil.nameEmail(author));
    fmt.getElement(0, 3).setTitle(FormatUtil.mediumFormat(when));
  }

  protected CommentPanel(CommentLinkProcessor commentLinkProcessor) {
    this.commentLinkProcessor = commentLinkProcessor;
    final FlowPanel body = new FlowPanel();
    initWidget(body);
    setStyleName(Gerrit.RESOURCES.css().commentPanel());

    messageSummary = new InlineLabel();
    messageSummary.setStyleName(Gerrit.RESOURCES.css().commentPanelSummary());

    header = new FlexTable();
    header.setStyleName(Gerrit.RESOURCES.css().commentPanelHeader());
    header.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        if (event.getSource() instanceof AvatarImage) {
          return;
        }
        setOpen(!isOpen());
      }
    });
    header.setText(0, 1, "");
    header.setWidget(0, 2, messageSummary);
    header.setText(0, 3, "");
    final CellFormatter fmt = header.getCellFormatter();
    fmt.setStyleName(0, 1, Gerrit.RESOURCES.css().commentPanelAuthorCell());
    fmt.setStyleName(0, 2, Gerrit.RESOURCES.css().commentPanelSummaryCell());
    fmt.setStyleName(0, 3, Gerrit.RESOURCES.css().commentPanelDateCell());
    fmt.setHorizontalAlignment(0, 3, HasHorizontalAlignment.ALIGN_RIGHT);
    body.add(header);

    content = new FlowPanel();
    content.setStyleName(Gerrit.RESOURCES.css().commentPanelContent());
    content.setVisible(false);
    body.add(content);

    messageText = new DoubleClickHTML();
    messageText.setStyleName(Gerrit.RESOURCES.css().commentPanelMessage());
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
    SafeHtml buf = new SafeHtmlBuilder().append(message).wikify();
    buf = commentLinkProcessor.apply(buf);
    SafeHtml.set(messageText, buf);
  }

  public void setAuthorNameText(final String authorEmail, final String nameText) {
    header.setWidget(0, 0, new AvatarImage(authorEmail, 26));
    header.setText(0, 1, nameText);
  }

  protected void setDateText(final String dateText) {
    header.setText(0, 3, dateText);
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

  /**
   * Registers a {@link FocusHandler} for this comment panel.
   * The comment panel is considered as being focused whenever any button in the
   * comment panel gets focused.
   *
   * @param handler the focus handler to be registered
   */
  @Override
  public HandlerRegistration addFocusHandler(final FocusHandler handler) {
    return handlerManager.addHandler(FocusEvent.getType(), handler);
  }

  /**
   * Registers a {@link BlurHandler} for this comment panel.
   * The comment panel is considered as being blurred whenever any button in the
   * comment panel gets blurred.
   *
   * @param handler the blur handler to be registered
   */
  @Override
  public HandlerRegistration addBlurHandler(final BlurHandler handler) {
    return handlerManager.addHandler(BlurEvent.getType(), handler);
  }

  protected void addButton(final Button button) {
    // register focus and blur handler for each button, so that we can fire
    // focus and blur events for the comment panel
    button.addFocusHandler(this);
    button.addBlurHandler(this);
    getButtonPanel().add(button);
  }

  private Panel getButtonPanel() {
    if (buttons == null) {
      buttons = new FlowPanel();
      buttons.setStyleName(Gerrit.RESOURCES.css().commentPanelButtons());
      content.add(buttons);
    }
    return buttons;
  }

  @Override
  public void onFocus(final FocusEvent event) {
    // a button was focused -> fire focus event for the comment panel
    handlerManager.fireEvent(event);
  }

  @Override
  public void onBlur(final BlurEvent event) {
    // a button was blurred -> fire blur event for the comment panel
    handlerManager.fireEvent(event);
  }

  public void enableButtons(final boolean on) {
    for (Widget w : getButtonPanel()) {
      if (w instanceof Button) {
        ((Button) w).setEnabled(on);
      }
    }
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
