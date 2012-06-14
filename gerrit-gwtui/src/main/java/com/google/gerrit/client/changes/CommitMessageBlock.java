// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.client.changes;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.ui.CommentLinkProcessor;
import com.google.gerrit.reviewdb.client.Change.Id;
import com.google.gwt.user.client.ui.Composite;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.PreElement;
import com.google.gwt.dom.client.Style.Display;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwtexpui.globalkey.client.KeyCommandSet;
import com.google.gwtexpui.safehtml.client.SafeHtml;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;

public class CommitMessageBlock extends Composite {
  interface Binder extends UiBinder<HTMLPanel, CommitMessageBlock> {
  }

  private static Binder uiBinder = GWT.create(Binder.class);

  private KeyCommandSet keysAction;

  @UiField
  SimplePanel starPanel;
  @UiField
  PreElement commitSummaryPre;
  @UiField
  PreElement commitBodyPre;

  public CommitMessageBlock() {
    keysAction = new KeyCommandSet(Gerrit.C.sectionActions());
    initWidget(uiBinder.createAndBindUi(this));
  }

  public void display(final String commitMessage) {
    display(null, commitMessage);
  }

  public void display(final Id changeId, final String commitMessage) {
    if (changeId != null && Gerrit.isSignedIn()) {
      StarredChanges.Icon star = StarredChanges.createIcon(changeId, false);
      star.setStyleName(Gerrit.RESOURCES.css().changeScreenStarIcon());
      starPanel.add(star);
      keysAction.add(StarredChanges.newKeyCommand(star));
    }

    String commitSummary = "";
    String commitBody = "";

    String[] splitCommitMessage = commitMessage.split("\n", 2);
    commitSummary = splitCommitMessage[0];
    if (splitCommitMessage.length > 1)
      commitBody = splitCommitMessage[1];

    // Hide commit body if there is no body
    if (commitBody.trim().isEmpty()) {
      commitBodyPre.getStyle().setDisplay(Display.NONE);
    }

    // Linkify commit summary
    SafeHtml commitSummaryLinkified = new SafeHtmlBuilder().append(commitSummary);
    commitSummaryLinkified = commitSummaryLinkified.linkify();
    commitSummaryLinkified = CommentLinkProcessor.apply(commitSummaryLinkified);

    // Linkify commit body
    SafeHtml commitBodyLinkified = new SafeHtmlBuilder().append(commitBody);
    commitBodyLinkified = commitBodyLinkified.linkify();
    commitBodyLinkified = CommentLinkProcessor.apply(commitBodyLinkified);

    commitSummaryPre.setInnerHTML(commitSummaryLinkified.asString());
    commitBodyPre.setInnerHTML(commitBodyLinkified.asString());
  }
}
