// Copyright (C) 2015 The Android Open Source Project
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

import com.google.gerrit.client.account.Util;
import com.google.gerrit.client.change.RelatedChanges.ChangeAndCommit;
import com.google.gerrit.client.change.RelatedChanges.Tab;
import com.google.gerrit.client.info.ChangeInfo;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwtexpui.globalkey.client.GlobalKey;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;
import com.google.gwtexpui.user.client.AutoCenterDialogBox;

class SubmitDialog extends AutoCenterDialogBox {
  interface Binder extends UiBinder<FlowPanel, SubmitDialog> {}
  private static final Binder uiBinder = GWT.create(Binder.class);

  @UiField Label submitTopicLabel;
  @UiField Label submitBranchLabel;
  @UiField Button cancel;
  @UiField Button submitWholeTopic;
  @UiField Button submitWithParents;
  @UiField FlowPanel sameTopicPanel;
  @UiField FlowPanel sameBranchPanel;

  private final Handler handler;
  private final ChangeInfo changeInfo;
  private final JsArray<ChangeAndCommit> topicChanges;

  private JsArray<ChangeAndCommit> brancahChanges;

  interface Handler {
    void onWithParentsSubmit();
    void onWithTopicSubmit();
  }

  SubmitDialog(ChangeInfo changeInfo,
      JsArray<ChangeAndCommit> topicChnages,
      JsArray<ChangeAndCommit> relatedBrancahChanges, Handler handler) {
    super(/* auto hide */false, /* modal */true);
    this.handler = handler;
    this.changeInfo = changeInfo;
    this.topicChanges = topicChnages;
    this.brancahChanges = relatedBrancahChanges;

    setWidget(uiBinder.createAndBindUi(this));
    getElement().setId("submit_dialog");

    String submitTopic = Util.C.submitTopicText();
    String submitBranch = Util.C.submitBranchText();
    submitTopicLabel.setText(Util.C.submitTopicHeader());
    submitBranchLabel.getElement().setInnerSafeHtml(new SafeHtmlBuilder()
        .openDiv()
        .append(Util.C.submitBranchHeader())
        .closeDiv());
    cancel.setHTML(new SafeHtmlBuilder()
        .openDiv()
        .append(Util.C.buttonCancel())
        .closeDiv());
    submitWithParents.setHTML(new SafeHtmlBuilder()
        .openDiv()
        .append(submitBranch)
        .closeDiv());
    submitWholeTopic.setHTML(new SafeHtmlBuilder()
        .openDiv()
        .append(submitTopic)
        .closeDiv());
  }

  @Override
  public void center() {
    super.center();
    GlobalKey.dialog(this);
    submitWithParents.setFocus(true);
    RelatedChangesTab submittedTopic =
        newRelatedChangesTab(Tab.SUBMITTED_TOGETHER);
    sameTopicPanel.add(submittedTopic);
    submittedTopic.setChanges(changeInfo.project(),
        changeInfo.currentRevision(), topicChanges);

    RelatedChangesTab submittedBranch =
        newRelatedChangesTab(Tab.SUBMITTED_TOGETHER);
    sameBranchPanel.add(submittedBranch);
    submittedBranch.setChanges(changeInfo.project(),
        changeInfo.currentRevision(), brancahChanges);
  }

  public RelatedChangesTab newRelatedChangesTab(Tab subject) {
    RelatedChangesTab changes = new RelatedChangesTab(subject);
    changes.setShowBranches(true);
    changes.setShowProjects(true);
    changes.setShowSubmittable(true);
    return changes;
  }

  @UiHandler("submitWithParents")
  void onOldSubmit(@SuppressWarnings("unused") ClickEvent e) {
    handler.onWithTopicSubmit();
    hide();
  }

  @UiHandler("submitWholeTopic")
  void onNewSubmit(@SuppressWarnings("unused") ClickEvent e) {
    handler.onWithParentsSubmit();
    hide();
  }

  @UiHandler("cancel")
  void onCancel(@SuppressWarnings("unused") ClickEvent e) {
    hide();
  }
}
