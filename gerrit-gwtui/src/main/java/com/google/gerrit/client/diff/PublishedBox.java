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

package com.google.gerrit.client.diff;

import com.google.gerrit.client.AvatarImage;
import com.google.gerrit.client.Dispatcher;
import com.google.gerrit.client.FormatUtil;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.api.ApiGlue;
import com.google.gerrit.client.change.ReplyBox;
import com.google.gerrit.client.changes.CommentApi;
import com.google.gerrit.client.changes.CommentInfo;
import com.google.gerrit.client.changes.Util;
import com.google.gerrit.client.patches.PatchUtil;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.CommentLinkProcessor;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.UIObject;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;

/** An HtmlPanel for displaying a published comment */
class PublishedBox extends CommentBox {
  interface Binder extends UiBinder<HTMLPanel, PublishedBox> {}

  private static final Binder uiBinder = GWT.create(Binder.class);

  interface Style extends CssResource {
    String closed();
  }

  private final PatchSet.Id psId;
  private final Project.NameKey project;
  private final CommentInfo comment;
  private final DisplaySide displaySide;
  private DraftBox replyBox;

  @UiField Style style;
  @UiField Widget header;
  @UiField Element name;
  @UiField Element summary;
  @UiField Element date;
  @UiField Element message;
  @UiField Element buttons;
  @UiField Button reply;
  @UiField Button done;
  @UiField Button fix;

  @UiField(provided = true)
  AvatarImage avatar;

  PublishedBox(
      CommentGroup group,
      CommentLinkProcessor clp,
      @Nullable Project.NameKey project,
      PatchSet.Id psId,
      CommentInfo info,
      DisplaySide displaySide,
      boolean open) {
    super(group, info.range());

    this.psId = psId;
    this.project = project;
    this.comment = info;
    this.displaySide = displaySide;

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

    name.setInnerText(authorName(info));
    date.setInnerText(FormatUtil.shortFormatDayTime(info.updated()));
    if (info.message() != null) {
      String msg = info.message().trim();
      summary.setInnerText(msg);
      message.setInnerSafeHtml(clp.apply(new SafeHtmlBuilder().append(msg).wikify()));
      ApiGlue.fireEvent("comment", message);
    }

    fix.setVisible(open);
  }

  @Override
  CommentInfo getCommentInfo() {
    return comment;
  }

  @Override
  boolean isOpen() {
    return UIObject.isVisible(message);
  }

  @Override
  void setOpen(boolean open) {
    UIObject.setVisible(summary, !open);
    UIObject.setVisible(message, open);
    UIObject.setVisible(buttons, open && replyBox == null);
    if (open) {
      removeStyleName(style.closed());
    } else {
      addStyleName(style.closed());
    }
    super.setOpen(open);
  }

  void setReplyBox(DraftBox box) {
    replyBox = box;
    UIObject.setVisible(buttons, false);
    box.setReplyToBox(this);
  }

  void unregisterReplyBox() {
    replyBox = null;
    UIObject.setVisible(buttons, isOpen());
  }

  private void openReplyBox() {
    replyBox.setOpen(true);
    replyBox.setEdit(true);
  }

  void addReplyBox(boolean quote) {
    CommentInfo commentReply = CommentInfo.createReply(comment);
    if (quote) {
      commentReply.message(ReplyBox.quote(comment.message()));
    }
    getCommentManager().addDraftBox(displaySide, commentReply).setEdit(true);
  }

  void doReply() {
    if (!Gerrit.isSignedIn()) {
      Gerrit.doSignIn(getCommentManager().host.getToken());
    } else if (replyBox == null) {
      addReplyBox(false);
    } else {
      openReplyBox();
    }
  }

  @UiHandler("reply")
  void onReply(ClickEvent e) {
    e.stopPropagation();
    doReply();
  }

  @UiHandler("quote")
  void onQuote(ClickEvent e) {
    e.stopPropagation();
    if (!Gerrit.isSignedIn()) {
      Gerrit.doSignIn(getCommentManager().host.getToken());
    }
    addReplyBox(true);
  }

  @UiHandler("done")
  void onReplyDone(ClickEvent e) {
    e.stopPropagation();
    if (!Gerrit.isSignedIn()) {
      Gerrit.doSignIn(getCommentManager().host.getToken());
    } else if (replyBox == null) {
      done.setEnabled(false);
      CommentInfo input = CommentInfo.createReply(comment);
      input.message(PatchUtil.C.cannedReplyDone());
      CommentApi.createDraft(
          Project.NameKey.asStringOrNull(project),
          psId,
          input,
          new GerritCallback<CommentInfo>() {
            @Override
            public void onSuccess(CommentInfo result) {
              done.setEnabled(true);
              setOpen(false);
              getCommentManager().addDraftBox(displaySide, result);
            }
          });
    } else {
      openReplyBox();
      setOpen(false);
    }
  }

  @UiHandler("fix")
  void onFix(ClickEvent e) {
    e.stopPropagation();
    String t = Dispatcher.toEditScreen(project, psId, comment.path(), comment.line());
    if (!Gerrit.isSignedIn()) {
      Gerrit.doSignIn(t);
    } else {
      Gerrit.display(t);
    }
  }

  private static String authorName(CommentInfo info) {
    if (info.author() != null) {
      if (info.author().name() != null) {
        return info.author().name();
      }
      return Gerrit.info().user().anonymousCowardName();
    }
    return Util.C.messageNoAuthor();
  }
}
