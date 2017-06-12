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
import com.google.gerrit.client.info.AccountInfo;
import com.google.gerrit.client.info.ChangeInfo;
import com.google.gerrit.client.info.ChangeInfo.CommitInfo;
import com.google.gerrit.client.info.ChangeInfo.GitPerson;
import com.google.gerrit.client.info.ChangeInfo.RevisionInfo;
import com.google.gerrit.client.info.WebLinkInfo;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.client.ui.CommentLinkProcessor;
import com.google.gerrit.client.ui.InlineHyperlink;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.PageLinks;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.TableRowElement;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.UIObject;
import com.google.gwtexpui.clippy.client.CopyableLabel;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;

class CommitBox extends Composite {
  interface Binder extends UiBinder<HTMLPanel, CommitBox> {}

  private static final Binder uiBinder = GWT.create(Binder.class);

  interface Style extends CssResource {
    String collapsed();

    String expanded();

    String clippy();

    String parentWebLink();
  }

  @UiField Style style;
  @UiField FlowPanel authorPanel;
  @UiField FlowPanel committerPanel;
  @UiField Image mergeCommit;
  @UiField CopyableLabel commitName;
  @UiField FlowPanel webLinkPanel;
  @UiField TableRowElement firstParent;
  @UiField FlowPanel parentCommits;
  @UiField FlowPanel parentWebLinks;
  @UiField InlineHyperlink authorNameEmail;
  @UiField Element authorDate;
  @UiField InlineHyperlink committerNameEmail;
  @UiField Element committerDate;
  @UiField CopyableLabel idText;
  @UiField HTML text;
  @UiField ScrollPanel scroll;
  @UiField Button more;
  @UiField Element parentNotCurrentText;
  private boolean expanded;

  CommitBox() {
    initWidget(uiBinder.createAndBindUi(this));
    addStyleName(style.collapsed());
  }

  void onShowView() {
    more.setVisible(scroll.getMaximumVerticalScrollPosition() > 0);
  }

  @UiHandler("more")
  void onMore(@SuppressWarnings("unused") ClickEvent e) {
    if (expanded) {
      removeStyleName(style.expanded());
      addStyleName(style.collapsed());
    } else {
      removeStyleName(style.collapsed());
      addStyleName(style.expanded());
    }
    expanded = !expanded;
  }

  void set(CommentLinkProcessor commentLinkProcessor, ChangeInfo change, String revision) {
    RevisionInfo revInfo = change.revision(revision);
    CommitInfo commit = revInfo.commit();

    commitName.setText(revision);
    idText.setText("Change-Id: " + change.changeId());
    idText.setPreviewText(change.changeId());

    formatLink(commit.author(), authorPanel, authorNameEmail, authorDate, change);
    formatLink(commit.committer(), committerPanel, committerNameEmail, committerDate, change);
    text.setHTML(
        commentLinkProcessor.apply(new SafeHtmlBuilder().append(commit.message()).linkify()));
    setWebLinks(revInfo);

    if (revInfo.commit().parents().length() > 1) {
      mergeCommit.setVisible(true);
    }

    setParents(revInfo.commit().parents());
  }

  void setParentNotCurrent(boolean parentNotCurrent) {
    // display the orange ball if parent has moved on (not current)
    UIObject.setVisible(parentNotCurrentText, parentNotCurrent);
    parentNotCurrentText.setInnerText(parentNotCurrent ? "\u25CF" : "");
  }

  private void setWebLinks(RevisionInfo revInfo) {
    JsArray<WebLinkInfo> links = revInfo.commit().webLinks();
    if (links != null) {
      for (WebLinkInfo link : Natives.asList(links)) {
        webLinkPanel.add(link.toAnchor());
      }
    }
  }

  private void setParents(JsArray<CommitInfo> commits) {
    setVisible(firstParent, true);
    TableRowElement next = firstParent;
    TableRowElement previous = null;
    for (CommitInfo c : Natives.asList(commits)) {
      if (next == firstParent) {
        CopyableLabel copyLabel = getCommitLabel(c);
        parentCommits.add(copyLabel);
      } else {
        next.appendChild(DOM.createTD());
        Element td1 = DOM.createTD();
        td1.appendChild(getCommitLabel(c).getElement());
        next.appendChild(td1);
        previous.getParentElement().insertAfter(next, previous);
      }
      previous = next;
      next = DOM.createTR().cast();
    }
  }

  private CopyableLabel getCommitLabel(CommitInfo c) {
    CopyableLabel copyLabel;
    copyLabel = new CopyableLabel(c.commit());
    copyLabel.setTitle(c.subject());
    copyLabel.setStyleName(style.clippy());
    return copyLabel;
  }

  private static void formatLink(
      GitPerson person, FlowPanel p, InlineHyperlink name, Element date, ChangeInfo change) {
    // only try to fetch the avatar image for author and committer if an avatar
    // plugin is installed, if the change owner has no avatar info assume that
    // no avatar plugin is installed
    if (change.owner().hasAvatarInfo()) {
      AvatarImage avatar;
      if (sameEmail(change.owner(), person)) {
        avatar = new AvatarImage(change.owner());
      } else {
        avatar = new AvatarImage(AccountInfo.create(0, person.name(), person.email(), null));
      }
      p.insert(avatar, 0);
    }

    name.setText(renderName(person));
    name.setTargetHistoryToken(PageLinks.toAccountQuery(owner(person), change.status()));
    date.setInnerText(FormatUtil.mediumFormat(person.date()));
  }

  private static String renderName(GitPerson person) {
    return person.name() + " <" + person.email() + ">";
  }

  private static String owner(GitPerson person) {
    if (person.email() != null) {
      return person.email();
    } else if (person.name() != null) {
      return person.name();
    } else {
      return "";
    }
  }

  private static boolean sameEmail(@Nullable AccountInfo p1, @Nullable GitPerson p2) {
    return p1 != null
        && p2 != null
        && p1.email() != null
        && p2.email() != null
        && p1.email().equals(p2.email());
  }
}
