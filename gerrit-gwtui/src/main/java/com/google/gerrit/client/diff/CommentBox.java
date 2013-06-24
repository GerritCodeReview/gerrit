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
import com.google.gerrit.client.ui.CommentLinkProcessor;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style.Unit;
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

import net.codemirror.lib.LineWidget;

/** An HtmlPanel for displaying a comment */
abstract class CommentBox extends Composite {
  interface CommentBoxStyle extends CssResource {
    String open();
    String close();
  }

  private CommentLinkProcessor commentLinkProcessor;
  private HandlerRegistration headerClick;
  private CommentInfo original;
  private PatchSet.Id patchSetId;
  private LineWidget selfWidget;
  private LineWidget paddingWidget;
  private Element paddingWidgetEle;
  private CodeMirrorDemo diffView;

  @UiField(provided=true)
  CommentBoxHeader header;

  @UiField
  HTML contentPanelMessage;

  @UiField
  CommentBoxResources res;

  protected CommentBox(
      CodeMirrorDemo host,
      UiBinder<? extends Widget, CommentBox> binder,
      PatchSet.Id id, CommentInfo info, CommentLinkProcessor linkProcessor,
      boolean isDraft) {
    diffView = host;
    commentLinkProcessor = linkProcessor;
    original = info;
    patchSetId = id;
    header = new CommentBoxHeader(info.author(), info.updated(), isDraft);
    initWidget(binder.createAndBindUi(this));
    setMessageText(info.message());
    setOpen(false);
  }

  @Override
  protected void onLoad() {
    super.onLoad();

    headerClick = header.addDomHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        setOpen(!isOpen());
        resizePaddingWidget();
      }
    }, ClickEvent.getType());
    res.style().ensureInjected();
  }

  @Override
  protected void onUnload() {
    super.onUnload();

    if (headerClick != null) {
      headerClick.removeHandler();
      headerClick = null;
    }
  }

  void setSelfWidget(LineWidget widget) {
    selfWidget = widget;
  }

  void setPadding(LineWidget widget, Element element) {
    paddingWidget = widget;
    paddingWidgetEle = element;
  }

  void resizePaddingWidget() {
    paddingWidgetEle.getStyle().setHeight(getOffsetHeight(), Unit.PX);
  }

  protected void setMessageText(String message) {
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

  protected void setDateText(String dateText) {
    header.setDateText(dateText);
  }

  protected void setOpen(boolean open) {
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

  protected CodeMirrorDemo getDiffView() {
    return diffView;
  }

  protected PatchSet.Id getPatchSetId() {
    return patchSetId;
  }

  protected CommentInfo getOriginal() {
    return original;
  }

  protected LineWidget getSelfWidget() {
    return selfWidget;
  }

  protected LineWidget getPaddingWidget() {
    return paddingWidget;
  }

  protected void updateOriginal(CommentInfo newInfo) {
    original = newInfo;
  }
}
