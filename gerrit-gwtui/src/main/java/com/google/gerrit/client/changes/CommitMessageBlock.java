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
import com.google.gerrit.client.ui.ChangeLink;
import com.google.gerrit.client.ui.CommentLinkProcessor;
import com.google.gerrit.client.ui.CommentedActionDialog;
import com.google.gerrit.common.data.ChangeDetail;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.PreElement;
import com.google.gwt.dom.client.Style.Display;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwtexpui.clippy.client.CopyableLabel;
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
  FlowPanel permalinkPanel;
  @UiField
  PreElement commitSummaryPre;
  @UiField
  PreElement commitBodyPre;

  public CommitMessageBlock() {
    initWidget(uiBinder.createAndBindUi(this));
  }

  public CommitMessageBlock(KeyCommandSet keysAction) {
    this.keysAction = keysAction;
    initWidget(uiBinder.createAndBindUi(this));
  }

  public void display(final String commitMessage) {
    display(null, null, false, commitMessage);
  }

  public void display(final PatchSet.Id patchSetId,
      Boolean starred, Boolean canEditCommitMessage, final String commitMessage) {
    starPanel.clear();
    if (patchSetId != null && starred != null && Gerrit.isSignedIn()) {
      Change.Id changeId = patchSetId.getParentKey();
      StarredChanges.Icon star = StarredChanges.createIcon(changeId, starred);
      star.setStyleName(Gerrit.RESOURCES.css().changeScreenStarIcon());
      starPanel.add(star);

      if (keysAction != null) {
        keysAction.add(StarredChanges.newKeyCommand(star));
      }
    }

    permalinkPanel.clear();
    if (patchSetId != null) {
      Change.Id changeId = patchSetId.getParentKey();
      permalinkPanel.add(new ChangeLink(Util.C.changePermalink(), changeId));
      permalinkPanel.add(new CopyableLabel(ChangeLink.permalink(changeId),
          false));
      if (canEditCommitMessage) {
        final Image edit = new Image(Gerrit.RESOURCES.edit());
        edit.addClickHandler(new ClickHandler() {
          @Override
          public void onClick(final ClickEvent event) {
            new CommentedActionDialog<ChangeDetail>(Util.C
                .titleEditCommitMessage(), Util.C.headingEditCommitMessage(),
                new ChangeDetailCache.IgnoreErrorCallback() {}) {
              {
                message.setCharacterWidth(72);
                message.setVisibleLines(20);
                message.setText(commitMessage);
                message.addStyleName(Gerrit.RESOURCES.css()
                    .changeScreenDescription());
              }

              @Override
              public void onSend() {
                Util.MANAGE_SVC.createNewPatchSet(patchSetId, getMessageText(),
                    createCallback());
              }
            }.center();
          }
        });

        permalinkPanel.add(edit);
      }
    }

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
      commitBodyLinkified = commitBodyLinkified.replaceAll("\n\n", "<p></p>");
      commitBodyLinkified = commitBodyLinkified.replaceAll("\n", "<br />");
      commitBodyPre.setInnerHTML(commitBodyLinkified.asString());
    }
  }
}
