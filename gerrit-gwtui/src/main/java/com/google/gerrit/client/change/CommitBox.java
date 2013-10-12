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

import com.google.gerrit.client.FormatUtil;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.GitwebLink;
import com.google.gerrit.client.account.AccountInfo;
import com.google.gerrit.client.changes.ChangeInfo;
import com.google.gerrit.client.changes.ChangeInfo.CommitInfo;
import com.google.gerrit.client.changes.ChangeInfo.GitPerson;
import com.google.gerrit.client.changes.ChangeInfo.RevisionInfo;
import com.google.gerrit.client.ui.AccountLinkPanel;
import com.google.gerrit.client.ui.CommentLinkProcessor;
import com.google.gerrit.client.ui.InlineHyperlink;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.reviewdb.client.Change.Status;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.AnchorElement;
import com.google.gwt.dom.client.Element;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.UIObject;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;

class CommitBox extends Composite {
  interface Binder extends UiBinder<HTMLPanel, CommitBox> {}
  private static final Binder uiBinder = GWT.create(Binder.class);

  @UiField Element commitName;
  @UiField AnchorElement browserLink;
  @UiField InlineHyperlink authorNameEmail;
  @UiField Element authorDate;
  @UiField Element committerNameEmail;
  @UiField Element committerDate;
  @UiField Element commitMessageText;

  CommitBox() {
    initWidget(uiBinder.createAndBindUi(this));
  }

  void set(CommentLinkProcessor commentLinkProcessor,
      ChangeInfo change,
      String revision) {
    RevisionInfo revInfo = change.revision(revision);
    CommitInfo commit = revInfo.commit();

    commitName.setInnerText(revision);
    formatLink(commit.author(), authorNameEmail, authorDate,
        change.owner(), change.status());
    format(commit.committer(), committerNameEmail, committerDate);
    commitMessageText.setInnerSafeHtml(commentLinkProcessor.apply(
        new SafeHtmlBuilder().append(commit.message()).linkify()));

    GitwebLink gw = Gerrit.getGitwebLink();
    if (gw != null && gw.canLink(revInfo)) {
      browserLink.setInnerText(gw.getLinkName());
      browserLink.setHref(gw.toRevision(change.project(), revision));
    } else {
      UIObject.setVisible(browserLink, false);
    }
  }

  private void formatLink(GitPerson person, InlineHyperlink name,
      Element date, AccountInfo a, Status status) {
    name.setText(person.name() + " <" + person.email() + ">");
    name.setTargetHistoryToken(PageLinks.toAccountQuery(
        AccountLinkPanel.owner(a), status));
    date.setInnerText(FormatUtil.mediumFormat(person.date()));
  }

  private void format(GitPerson person, Element name, Element date) {
    name.setInnerText(person.name() + " <" + person.email() + ">");
    date.setInnerText(FormatUtil.mediumFormat(person.date()));
  }
}
