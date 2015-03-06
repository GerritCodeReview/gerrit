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
import com.google.gerrit.client.GitwebLink;
import com.google.gerrit.client.WebLinkInfo;
import com.google.gerrit.client.account.AccountInfo;
import com.google.gerrit.client.changes.ChangeInfo;
import com.google.gerrit.client.changes.ChangeInfo.CommitInfo;
import com.google.gerrit.client.changes.ChangeInfo.GitPerson;
import com.google.gerrit.client.changes.ChangeInfo.RevisionInfo;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.client.ui.CommentLinkProcessor;
import com.google.gerrit.client.ui.InlineHyperlink;
import com.google.gerrit.common.PageLinks;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtexpui.clippy.client.CopyableLabel;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;

class CommitBox extends Composite {
  interface Binder extends UiBinder<HTMLPanel, CommitBox> {
  }

  private static final Binder uiBinder = GWT.create(Binder.class);

  interface Style extends CssResource {
    String collapsed();
    String expanded();
    String clippy();
    String parentWebLink();
    String tableHeader();
    String userPanel();
    String date();
    String header();
    String commit();
    String webLinkPanel();
    String mergeCommitIcon();
  }

  @UiField Style style;
  @UiField HTML text;
  @UiField ScrollPanel scroll;
  @UiField Button more;
  @UiField FlexTable commitTable;

  private String project;
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

  void set(CommentLinkProcessor commentLinkProcessor,
      ChangeInfo change, String revision) {
    this.project = change.project();
    commitTable.addStyleName(style.header());
    RevisionInfo revInfo = change.revision(revision);
    CommitInfo commit = revInfo.commit();
    text.setHTML(commentLinkProcessor.apply(
        new SafeHtmlBuilder().append(commit.message()).linkify()));
    addGitPersonRow(change, commit.author(), "Author");
    addGitPersonRow(change, commit.committer(), "Committer");
    addCurrentCommitRow(revInfo);
    addParentsRows(commit.parents());
    addChangeIdRow(change.change_id());
  }

  private void addParentsRows(JsArray<CommitInfo> parents) {
    Label parentsHeader = new Label("Parent(s)");
    for (CommitInfo commit : Natives.asList(parents)) {
      addCommitRow(parentsHeader, commit, true);
      // Only header for the first parent-row
      parentsHeader = null;
    }
  }

  private void addChangeIdRow(String changeId) {
    CopyableLabel changeIdLabel = new CopyableLabel();
    changeIdLabel.setText("Change-Id: " + changeId);
    changeIdLabel.setPreviewText(changeId);
    changeIdLabel.setStyleName(style.clippy());
    Label header = new Label("Change-Id");
    addRow(header, changeIdLabel, null, null, null);
  }

  private void addCurrentCommitRow(RevisionInfo revInfo) {
    CommitInfo commit = revInfo.commit();
    FlowPanel headerPanel = new FlowPanel();
    Label header = new Label("Commit");
    header.addStyleName(style.commit());
    headerPanel.add(header);
    Image mergeCommitIcon = new Image(Gerrit.RESOURCES.merge());
    mergeCommitIcon.setStyleName(style.mergeCommitIcon());
    mergeCommitIcon.setTitle("Merge Commit");
    mergeCommitIcon.setVisible(commit.is_mergecommit());
    headerPanel.add(mergeCommitIcon);
    headerPanel.addStyleName(style.commit());
    GitwebLink gw = Gerrit.getGitwebLink();
    boolean createGitwebLink = gw != null ? gw.canLink(revInfo) : false;
    addCommitRow(headerPanel, commit, createGitwebLink);
  }

  private void addCommitRow(Widget headerWidget, CommitInfo commit, boolean createGitwebLink) {
    CopyableLabel commitLabel = new CopyableLabel(commit.commit());
    commitLabel.setTitle(commit.subject());
    commitLabel.setStyleName(style.clippy());
    commitLabel.setText(commit.commit());
    Widget links = getLinksFlowPanel(commit, createGitwebLink);
    addRow(headerWidget, commitLabel, null, links, style.webLinkPanel());
  }

  private void addGitPersonRow(ChangeInfo change, GitPerson gitPerson, String header) {
    Label committerDate = new Label();
    FlowPanel panel = new FlowPanel();
    formatLink(gitPerson, panel, committerDate, change);
    addRow(new Label(header), panel, style.userPanel(), committerDate,
        style.date());
  }

  private Widget getLinksFlowPanel(CommitInfo commit, boolean createGitwebLink) {
    FlowPanel linksPanel = new FlowPanel();
    GitwebLink gw = Gerrit.getGitwebLink();
    if (gw != null && createGitwebLink) {
      Anchor a =
          new Anchor(gw.getLinkName(), gw.toRevision(project, commit.commit()));
      linksPanel.add(a);
    }
    JsArray<WebLinkInfo> links = commit.web_links();
    if (links != null) {
      for (WebLinkInfo link : Natives.asList(links)) {
        Anchor wl = link.toAnchor();
        linksPanel.add(wl);
      }
    }
    linksPanel.addStyleName(style.webLinkPanel());
    return linksPanel;
  }

  private void addRow(Widget header, Widget col1, String col1Style,
      Widget col2, String col2Style) {
    int row = commitTable.getRowCount();
    CellFormatter cellFormatter = commitTable.getCellFormatter();
    if (header != null) {
      commitTable.setWidget(row, 0, header);
      cellFormatter.addStyleName(row, 0, style.tableHeader());
    }
    commitTable.setWidget(row, 1, col1);
    if (col1Style != null) {
      cellFormatter.addStyleName(row, 1, col1Style);
    }
    if (col2 != null) {
      commitTable.setWidget(row, 2, col2);
      if (col2Style != null) {
        cellFormatter.addStyleName(row, 2, col2Style);
      }
    }
  }

  private static void formatLink(GitPerson person, FlowPanel p,
      Label date, ChangeInfo change) {
    InlineHyperlink name = new InlineHyperlink();
    name.setText(renderName(person));
    name.setTargetHistoryToken(PageLinks.toAccountQuery(owner(person),
        change.status()));
    date.setText(FormatUtil.mediumFormat(person.date()));
    p.insert(name, 0);
    // only try to fetch the avatar image for author and committer if an avatar
    // plugin is installed, if the change owner has no avatar info assume that
    // no avatar plugin is installed
    if (change.owner().has_avatar_info()) {
      AvatarImage avatar;
      if (change.owner().email().equals(person.email())) {
        avatar = new AvatarImage(change.owner());
      } else {
        avatar = new AvatarImage(
                AccountInfo.create(0, person.name(), person.email(), null));
      }
      p.insert(avatar, 0);
    }
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
}
