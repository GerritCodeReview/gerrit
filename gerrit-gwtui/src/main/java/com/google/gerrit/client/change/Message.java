// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.client.change;

import com.google.gerrit.client.AvatarImage;
import com.google.gerrit.client.FormatUtil;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.api.ApiGlue;
import com.google.gerrit.client.changes.CommentInfo;
import com.google.gerrit.client.changes.Util;
import com.google.gerrit.client.info.ChangeInfo.MessageInfo;
import com.google.gerrit.client.ui.CommentLinkProcessor;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style.Visibility;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.UIObject;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

class Message extends Composite {
  interface Binder extends UiBinder<HTMLPanel, Message> {}

  private static final Binder uiBinder = GWT.create(Binder.class);

  interface Style extends CssResource {
    String closed();
  }

  @UiField Style style;
  @UiField HTMLPanel header;
  @UiField Element name;
  @UiField Element summary;
  @UiField Element date;
  @UiField Button reply;
  @UiField Element message;
  @UiField FlowPanel comments;

  private final History history;
  private final MessageInfo info;
  private List<CommentInfo> commentList;
  private boolean autoOpen;

  @UiField(provided = true)
  AvatarImage avatar;

  Message(History parent, MessageInfo info) {
    if (info.author() != null) {
      avatar = new AvatarImage(info.author());
      avatar.setSize("", "");
    } else {
      avatar = new AvatarImage();
    }

    initWidget(uiBinder.createAndBindUi(this));
    header.addDomHandler(
        new ClickHandler() {
          @Override
          public void onClick(ClickEvent event) {
            setOpen(!isOpen());
          }
        },
        ClickEvent.getType());

    this.history = parent;
    this.info = info;

    setName(false);
    date.setInnerText(FormatUtil.shortFormatDayTime(info.date()));
    if (info.message() != null) {
      String msg = info.message().trim();
      summary.setInnerText(msg);
      message.setInnerSafeHtml(
          history.getCommentLinkProcessor().apply(new SafeHtmlBuilder().append(msg).wikify()));
      ApiGlue.fireEvent("comment", message);
    } else {
      reply.getElement().getStyle().setVisibility(Visibility.HIDDEN);
    }
  }

  @UiHandler("reply")
  void onReply(ClickEvent e) {
    e.stopPropagation();

    if (Gerrit.isSignedIn()) {
      history.replyTo(info);
    } else {
      Gerrit.doSignIn(com.google.gwt.user.client.History.getToken());
    }
  }

  MessageInfo getMessageInfo() {
    return info;
  }

  private boolean isOpen() {
    return UIObject.isVisible(message);
  }

  void setOpen(boolean open) {
    if (open && info._revisionNumber() > 0 && !commentList.isEmpty()) {
      renderComments(commentList);
      commentList = Collections.emptyList();
    }
    setName(open);

    UIObject.setVisible(summary, !open);
    UIObject.setVisible(message, open);
    comments.setVisible(open && comments.getWidgetCount() > 0);
    if (open) {
      removeStyleName(style.closed());
    } else {
      addStyleName(style.closed());
    }
  }

  private void setName(boolean open) {
    name.setInnerText(
        open ? authorName(info) : com.google.gerrit.common.FormatUtil.elide(authorName(info), 20));
  }

  void autoOpen() {
    if (commentList == null) {
      autoOpen = true;
    } else if (!commentList.isEmpty()) {
      setOpen(true);
    }
  }

  void addComments(List<CommentInfo> list) {
    if (isOpen()) {
      renderComments(list);
      comments.setVisible(comments.getWidgetCount() > 0);
      commentList = Collections.emptyList();
    } else {
      commentList = list;
      if (autoOpen && !commentList.isEmpty()) {
        setOpen(true);
      }
    }
  }

  private void renderComments(List<CommentInfo> list) {
    CommentLinkProcessor clp = history.getCommentLinkProcessor();
    PatchSet.Id ps = new PatchSet.Id(history.getChangeId(), info._revisionNumber());
    TreeMap<String, List<CommentInfo>> m = byPath(list);
    List<CommentInfo> l = m.remove(Patch.COMMIT_MSG);
    if (l != null) {
      comments.add(new FileComments(clp, history.getProject(), ps, Util.C.commitMessage(), l));
    }
    l = m.remove(Patch.MERGE_LIST);
    if (l != null) {
      comments.add(new FileComments(clp, history.getProject(), ps, Util.C.mergeList(), l));
    }
    for (Map.Entry<String, List<CommentInfo>> e : m.entrySet()) {
      comments.add(new FileComments(clp, history.getProject(), ps, e.getKey(), e.getValue()));
    }
  }

  private static TreeMap<String, List<CommentInfo>> byPath(List<CommentInfo> list) {
    TreeMap<String, List<CommentInfo>> m = new TreeMap<>();
    for (CommentInfo c : list) {
      List<CommentInfo> l = m.get(c.path());
      if (l == null) {
        l = new ArrayList<>();
        m.put(c.path(), l);
      }
      l.add(c);
    }
    return m;
  }

  static String authorName(MessageInfo info) {
    if (info.author() != null) {
      if (info.author().name() != null) {
        return info.author().name();
      }
      return Gerrit.info().user().anonymousCowardName();
    }
    return Util.C.messageNoAuthor();
  }
}
