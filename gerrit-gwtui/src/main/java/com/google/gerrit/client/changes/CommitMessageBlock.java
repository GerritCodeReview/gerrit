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

import com.google.gerrit.client.ui.CommentLinkProcessor;
import com.google.gwt.user.client.ui.Composite;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.PreElement;
import com.google.gwt.dom.client.Style.Display;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwtexpui.safehtml.client.SafeHtml;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;

public class CommitMessageBlock extends Composite {
  interface Binder extends UiBinder<HTMLPanel, CommitMessageBlock> {
  }

  private static Binder uiBinder = GWT.create(Binder.class);

  @UiField
  PreElement commitSummaryPre;
  @UiField
  PreElement commitBodyPre;

  public CommitMessageBlock() {
    initWidget(uiBinder.createAndBindUi(this));
  }

  public void display(final String commitMessage) {
    String[] splitCommitMessage = commitMessage.split("\n", 2);

    String commitSummary = splitCommitMessage[0];
    String commitBody = "";
    if (splitCommitMessage.length > 1) {
      commitBody = splitCommitMessage[1];
    }

    // Linkify commit summary
    SafeHtml commitSummaryLinkified = new SafeHtmlBuilder().append(commitSummary);
    commitSummaryLinkified = commitSummaryLinkified.linkify();
    commitSummaryLinkified = CommentLinkProcessor.apply(commitSummaryLinkified);
    commitSummaryPre.setInnerHTML(commitSummaryLinkified.asString());

    // Hide commit body if there is no body
    if (commitBody.trim().isEmpty()) {
      commitBodyPre.getStyle().setDisplay(Display.NONE);
    } else {
      // Linkify commit body
      SafeHtml commitBodyLinkified = new SafeHtmlBuilder().append(commitBody);
      commitBodyLinkified = commitBodyLinkified.linkify();
      commitBodyLinkified = CommentLinkProcessor.apply(commitBodyLinkified);
      commitBodyPre.setInnerHTML(commitBodyLinkified.asString());
    }
  }
}
