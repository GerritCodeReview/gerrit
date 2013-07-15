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
import com.google.gerrit.client.patches.PatchUtil;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.HasClickHandlers;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;

import java.sql.Timestamp;

/**
 * An HtmlPanel representing the header of a CommentBox, displaying
 * the author's avatar (if applicable), the author's name, the summary,
 * and the date.
 */
class CommentBoxHeader extends Composite implements HasClickHandlers {
  interface Binder extends UiBinder<HTMLPanel, CommentBoxHeader> {}
  private static Binder uiBinder = GWT.create(Binder.class);

  interface CommentBoxHeaderStyle extends CssResource {
    String noAvatar();
    String name();
    String summary();
    String date();
  }

  private boolean draft;

  @UiField
  Element avatarCell;

  @UiField(provided=true)
  AvatarImage avatar;

  @UiField
  Element name;

  @UiField
  Element summary;

  @UiField
  Element date;

  @UiField
  CommentBoxHeaderStyle headerStyle;

  CommentBoxHeader(AccountInfo author, Timestamp when, boolean isDraft) {
    if (author != null) {
      avatar = new AvatarImage(author, 26);
      avatar.setSize("", "");
    } else {
      avatar = new AvatarImage();
    }
    initWidget(uiBinder.createAndBindUi(this));
    if (author == null) {
      avatarCell.addClassName(headerStyle.noAvatar());
    }
    draft = isDraft;
    if (when != null) {
      setDate(when);
    }
    if (isDraft) {
      name.setInnerText(PatchUtil.C.draft());
    } else {
      name.setInnerText(FormatUtil.name(author));
      name.setTitle(FormatUtil.nameEmail(author));
      date.setTitle(FormatUtil.mediumFormat(when));
    }
  }

  void setDate(Timestamp when) {
    if (draft) {
      date.setInnerText(PatchUtil.M.draftSaved(when));
    } else {
      date.setInnerText(FormatUtil.shortFormatDayTime(when));
    }
  }

  void setSummaryText(String message) {
    summary.setInnerText(message);
  }

  @Override
  public HandlerRegistration addClickHandler(ClickHandler handler) {
    return addDomHandler(handler, ClickEvent.getType());
  }
}
