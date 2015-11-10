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

  @UiField Label header;
  @UiField Label footer;
  @UiField Button cancel;
  @UiField Button submitBranchButton;
  @UiField Button submitTopicButton;
  @UiField FlowPanel sameTopicChangesPanel;

  private final Handler handler;
  private final ChangeInfo changeInfo;
  private final JsArray<ChangeAndCommit> sameTopicChanges;

  interface Handler {
    void onBranchSubmit();
    void onTopicSubmit();
  }

  SubmitDialog(ChangeInfo changeInfo,
      JsArray<ChangeAndCommit> sameTopicChnages,
      Handler handler) {
    super(false, true);
    this.changeInfo = changeInfo;
    this.sameTopicChanges = sameTopicChnages;
    this.handler = handler;

    setWidget(uiBinder.createAndBindUi(this));
    getElement().setId("submit_dialog");

    String submitTopicLabel = Resources.C.submitTopicText();
    String submitBranchLabel = Resources.C.submitBranchText();
    header.setText(Resources.C.submitDialogHeader());
    footer.getElement().setInnerSafeHtml(new SafeHtmlBuilder()
        .openDiv()
        .append(Resources.M.submitDialogFooter(submitTopicLabel, submitBranchLabel))
        .closeDiv());
    cancel.setHTML(new SafeHtmlBuilder()
        .openDiv()
        .append(Resources.C.submitDialogCancel())
        .closeDiv());
    submitBranchButton.setHTML(new SafeHtmlBuilder()
        .openDiv()
        .append(submitBranchLabel)
        .closeDiv());
    submitBranchButton.setTitle(Resources.C.submitBranchTitle());
    submitTopicButton.setHTML(new SafeHtmlBuilder()
        .openDiv()
        .append(submitTopicLabel)
        .closeDiv());
    submitTopicButton.setTitle(Resources.C.submitTopicTitle());
  }

  @Override
  public void center() {
    super.center();
    GlobalKey.dialog(this);
    submitBranchButton.setFocus(true);
    RelatedChangesTab submittedTogether = new RelatedChangesTab(Tab.SUBMITTED_TOGETHER);
    sameTopicChangesPanel.add(submittedTogether);
    submittedTogether.setShowBranches(true);
    submittedTogether.setShowProjects(true);
    submittedTogether.setShowSubmittable(true);
    submittedTogether.setChanges(changeInfo.project(),
        changeInfo.currentRevision(), sameTopicChanges);
  }

  @UiHandler("submitBranchButton")
  void onBranchSubmit(@SuppressWarnings("unused") ClickEvent e) {
    handler.onBranchSubmit();
    hide();
  }

  @UiHandler("submitTopicButton")
  void onTopicSubmit(@SuppressWarnings("unused") ClickEvent e) {
    handler.onTopicSubmit();
    hide();
  }

  @UiHandler("cancel")
  void onCancel(@SuppressWarnings("unused") ClickEvent e) {
    hide();
  }
}
