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

  @UiField Label header;
  @UiField Label footer;
  @UiField Button cancel;
  @UiField Button submitWithParents;
  @UiField Button submitWholeTopic;
  @UiField FlowPanel sameTopicChanges;

  private final Handler handler;
  private final ChangeInfo changeInfo;
  private final JsArray<ChangeAndCommit> topicChanges;

  interface Handler {
    void onWithParentsSubmit();
    void onWithTopicSubmit();
  }

  SubmitDialog(ChangeInfo changeInfo, JsArray<ChangeAndCommit> topicChnages,
      Handler handler) {
    super(/* auto hide */false, /* modal */true);
    this.handler = handler;
    this.changeInfo = changeInfo;
    this.topicChanges = topicChnages;

    setWidget(uiBinder.createAndBindUi(this));
    getElement().setId("submit_dialog");

    String wholeTopic = Util.C.submitWholeTopicText();
    String withParents = Util.C.submitWithParentsText();
    header.setText(Util.C.submitDialogHeader());
    footer.getElement().setInnerSafeHtml(new SafeHtmlBuilder()
        .openDiv()
        .append(Util.M.submitDialogFooter(wholeTopic, withParents))
        .closeDiv());
    cancel.setHTML(new SafeHtmlBuilder()
        .openDiv()
        .append(Util.C.buttonCancel())
        .closeDiv());
    submitWithParents.setHTML(new SafeHtmlBuilder()
        .openDiv()
        .append(wholeTopic)
        .closeDiv());
    submitWithParents.setTitle(Util.C.oldSubmitTitle());
    submitWholeTopic.setHTML(new SafeHtmlBuilder()
        .openDiv()
        .append(withParents)
        .closeDiv());
    submitWholeTopic.setTitle(Util.C.newSubmitTitle());
  }

  @Override
  public void center() {
    super.center();
    GlobalKey.dialog(this);
    submitWithParents.setFocus(true);
    RelatedChangesTab submittedTogether = new RelatedChangesTab(Tab.SUBMITTED_TOGETHER);
    sameTopicChanges.add(submittedTogether);
    submittedTogether.setShowBranches(true);
    submittedTogether.setShowProjects(true);
    submittedTogether.setShowSubmittable(true);
    submittedTogether.setChanges(changeInfo.project(),
        changeInfo.currentRevision(), topicChanges);
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
