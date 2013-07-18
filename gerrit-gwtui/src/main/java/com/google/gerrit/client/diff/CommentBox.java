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

import com.google.gerrit.client.changes.CommentInfo;
import com.google.gerrit.client.diff.SidePanel.GutterWrapper;
import com.google.gerrit.client.ui.CommentLinkProcessor;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtexpui.safehtml.client.SafeHtml;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;

import net.codemirror.lib.CodeMirror;
import net.codemirror.lib.LineWidget;

import java.sql.Timestamp;

/** An HtmlPanel for displaying a comment */
abstract class CommentBox extends Composite {
  interface CommentBoxStyle extends CssResource {
    String open();
    String close();
  }

  private CommentLinkProcessor commentLinkProcessor;
  private CommentInfo original;
  private PatchSet.Id patchSetId;
  private PaddingManager widgetManager;
  private SideBySide2 diffView;
  private boolean draft;
  private LineWidget selfWidget;
  private CodeMirror cm;
  private HandlerRegistration regClick;
  private ClickHandler clickFocusHandler;
  private GutterWrapper gutterWrapper;

  @UiField(provided=true)
  CommentBoxHeader header;

  @UiField
  HTML contentPanelMessage;

  @UiField
  CommentBoxResources res;

  CommentBox(
      SideBySide2 host,
      CodeMirror cmInstance,
      UiBinder<? extends Widget, CommentBox> binder,
      PatchSet.Id id, CommentInfo info, CommentLinkProcessor linkProcessor,
      boolean isDraft) {
    diffView = host;
    cm = cmInstance;
    commentLinkProcessor = linkProcessor;
    original = info;
    patchSetId = id;
    draft = isDraft;
    header = new CommentBoxHeader(info.author(), info.updated(), isDraft);
    initWidget(binder.createAndBindUi(this));
    clickFocusHandler = new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        cm.focus();
      }
    };
    enableClickFocusHandler();
    res.style().ensureInjected();
    setMessageText(info.message());
  }

  void resizePaddingWidget() {
    Scheduler.get().scheduleDeferred(new ScheduledCommand(){
      public void execute() {
        if (selfWidget == null || widgetManager == null) {
          throw new IllegalStateException(
              "resizePaddingWidget() called before setting up widgets");
        }
        selfWidget.changed();
        widgetManager.resizePaddingWidget();
      }
    });
  }

  void setMessageText(String message) {
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

  void setDate(Timestamp when) {
    header.setDate(when);
  }

  void setOpen(boolean open) {
    if (open) {
      removeStyleName(res.style().close());
      addStyleName(res.style().open());
    } else {
      removeStyleName(res.style().open());
      addStyleName(res.style().close());
    }
    resizePaddingWidget();
  }

  boolean isOpen() {
    return getStyleName().contains(res.style().open());
  }

  SideBySide2 getDiffView() {
    return diffView;
  }

  PatchSet.Id getPatchSetId() {
    return patchSetId;
  }

  CommentInfo getOriginal() {
    return original;
  }

  void updateOriginal(CommentInfo newInfo) {
    original = newInfo;
  }

  PaddingManager getPaddingManager() {
    return widgetManager;
  }

  boolean isDraft() {
    return draft;
  }

  void setPaddingManager(PaddingManager manager) {
    widgetManager = manager;
  }

  void setSelfWidget(LineWidget widget) {
    selfWidget = widget;
  }

  void setGutterWrapper(GutterWrapper wrapper) {
    gutterWrapper = wrapper;
  }

  GutterWrapper getGutterWrapper() {
    return gutterWrapper;
  }

  LineWidget getSelfWidget() {
    return selfWidget;
  }

  CodeMirror getCm() {
    return cm;
  }

  @UiHandler("header")
  void onHeaderClick(ClickEvent e) {
    setOpen(!isOpen());
    cm.focus();
  }

  void enableClickFocusHandler() {
    if (regClick == null) {
      regClick = addDomHandler(clickFocusHandler, ClickEvent.getType());
    }
  }

  void disableClickFocusHandler() {
    if (regClick != null) {
      regClick.removeHandler();
      regClick = null;
    }
  }
}
