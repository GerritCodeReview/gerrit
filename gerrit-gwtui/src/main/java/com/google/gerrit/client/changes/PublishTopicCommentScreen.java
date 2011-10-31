// Copyright (C) 2011 The Android Open Source Project
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
import com.google.gerrit.client.patches.CommentEditorContainer;
import com.google.gerrit.client.patches.CommentEditorPanel;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.AccountScreen;
import com.google.gerrit.client.ui.SmallHeading;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.common.data.ApprovalType;
import com.google.gerrit.common.data.ApprovalTypes;
import com.google.gerrit.common.data.ChangeSetPublishDetail;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRange;
import com.google.gerrit.common.data.TopicDetail;
import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.ApprovalCategoryValue;
import com.google.gerrit.reviewdb.ChangeSet;
import com.google.gerrit.reviewdb.ChangeSetApproval;
import com.google.gerrit.reviewdb.Topic;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.FormPanel;
import com.google.gwt.user.client.ui.FormPanel.SubmitEvent;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.RadioButton;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtexpui.globalkey.client.NpTextArea;
import com.google.gwtjsonrpc.client.VoidResult;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class PublishTopicCommentScreen extends AccountScreen implements
    ClickHandler, CommentEditorContainer {
  private static SavedState lastState;

  private final ChangeSet.Id changeSetId;
  private Collection<ValueRadioButton> approvalButtons;
  private TopicDescriptionBlock descBlock;
  private Panel approvalPanel;
  private NpTextArea message;
  private Button send;
  private Button submit;
  private Button cancel;
  private boolean saveStateOnUnload = true;

  public PublishTopicCommentScreen(final ChangeSet.Id csi) {
    changeSetId = csi;
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();
    addStyleName(Gerrit.RESOURCES.css().publishCommentsScreen());

    approvalButtons = new ArrayList<ValueRadioButton>();

    descBlock = new TopicDescriptionBlock();
    add(descBlock);

    final FormPanel form = new FormPanel();
    final FlowPanel body = new FlowPanel();
    form.setWidget(body);
    form.addSubmitHandler(new FormPanel.SubmitHandler() {
      @Override
      public void onSubmit(final SubmitEvent event) {
        event.cancel();
      }
    });
    add(form);

    approvalPanel = new FlowPanel();
    body.add(approvalPanel);
    initMessage(body);

    final FlowPanel buttonRow = new FlowPanel();
    buttonRow.setStyleName(Gerrit.RESOURCES.css().patchSetActions());
    body.add(buttonRow);

    send = new Button(Util.TC.buttonPublishCommentsSend());
    send.addClickHandler(this);
    buttonRow.add(send);

    submit = new Button(Util.TC.buttonPublishSubmitSend());
    submit.addClickHandler(this);
    buttonRow.add(submit);

    cancel = new Button(Util.TC.buttonPublishCommentsCancel());
    cancel.addClickHandler(this);
    buttonRow.add(cancel);
  }

  private void enableForm(final boolean enabled) {
    for (final ValueRadioButton approvalButton : approvalButtons) {
      approvalButton.setEnabled(enabled);
    }
    message.setEnabled(enabled);
    send.setEnabled(enabled);
    submit.setEnabled(enabled);
    cancel.setEnabled(enabled);
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    Util.T_DETAIL_SVC.changeSetPublishDetail(changeSetId,
        new ScreenLoadCallback<ChangeSetPublishDetail>(this) {
          @Override
          protected void preDisplay(final ChangeSetPublishDetail result) {
            send.setEnabled(true);
            display(result);
          }

          @Override
          protected void postDisplay() {
            message.setFocus(true);
          }
        });
  }

  @Override
  protected void onUnload() {
    super.onUnload();
    lastState = saveStateOnUnload ? new SavedState(this) : null;
  }

  @Override
  public void onClick(final ClickEvent event) {
    final Widget sender = (Widget) event.getSource();
    if (send == sender) {
      onSend(false);
    } else if (submit == sender) {
      onSend(true);
    } else if (cancel == sender) {
      saveStateOnUnload = false;
      goChangeSet();
    }
  }

  @Override
  public void notifyDraftDelta(int delta) {
  }

  @Override
  public void remove(CommentEditorPanel editor) {
  }

  private void initMessage(final Panel body) {
    body.add(new SmallHeading(Util.TC.headingCoverMessage()));

    final VerticalPanel mwrap = new VerticalPanel();
    mwrap.setStyleName(Gerrit.RESOURCES.css().coverMessage());
    body.add(mwrap);

    message = new NpTextArea();
    message.setCharacterWidth(60);
    message.setVisibleLines(10);
    message.setSpellCheck(true);
    mwrap.add(message);
  }

  private void initApprovals(final ChangeSetPublishDetail r, final Panel body) {
    ApprovalTypes types = Gerrit.getConfig().getApprovalTypes();

    for (ApprovalType type : types.getApprovalTypes()) {
      String permission = Permission.forLabel(type.getCategory().getLabelName());
      PermissionRange range = r.getRange(permission);
      if (range != null && !range.isEmpty()) {
        initApprovalType(r, body, type, range);
      }
    }

    for (PermissionRange range : r.getLabels()) {
      if (!range.isEmpty() && types.byLabel(range.getLabel()) == null) {
        // TODO: this is a non-standard label. Offer it without the type.
      }
    }
  }

  private void initApprovalType(final ChangeSetPublishDetail r,
      final Panel body, final ApprovalType ct, final PermissionRange range) {
    body.add(new SmallHeading(ct.getCategory().getName() + ":"));

    final VerticalPanel vp = new VerticalPanel();
    vp.setStyleName(Gerrit.RESOURCES.css().approvalCategoryList());
    final List<ApprovalCategoryValue> lst =
        new ArrayList<ApprovalCategoryValue>(ct.getValues());
    Collections.reverse(lst);
    final ApprovalCategory.Id catId = ct.getCategory().getId();
    final ChangeSetApproval prior = r.getChangeApproval(catId);

    for (final ApprovalCategoryValue buttonValue : lst) {
      if (!range.contains(buttonValue.getValue())) {
        continue;
      }

      final ValueRadioButton b =
          new ValueRadioButton(buttonValue, ct.getCategory().getName());
      b.setText(buttonValue.format());

      if (lastState != null && changeSetId.equals(lastState.changeSetId)
          && lastState.approvals.containsKey(buttonValue.getCategoryId())) {
        b.setValue(lastState.approvals.get(buttonValue.getCategoryId()).equals(
            buttonValue));
      } else {
        b.setValue(prior != null ? buttonValue.getValue() == prior.getValue()
            : buttonValue.getValue() == 0);
      }

      approvalButtons.add(b);
      vp.add(b);
    }
    body.add(vp);
  }

  private void display(final ChangeSetPublishDetail r) {
    setPageTitle(Util.TM.publishCommentsOnSet(r.getTopic().getKey().abbreviate(),
        r.getTopic().getTopic()));
    descBlock.display(r.getTopic(), r.getChangeSetInfo(), r.getAccounts());

    if (r.getTopic().getStatus().isOpen()) {
      initApprovals(r, approvalPanel);
    }

    if (lastState != null && changeSetId.equals(lastState.changeSetId)) {
      message.setText(lastState.message);
    }

    submit.setVisible(r.canSubmit());
  }

  private void onSend(final boolean submit) {
    final Map<ApprovalCategory.Id, ApprovalCategoryValue.Id> values =
        new HashMap<ApprovalCategory.Id, ApprovalCategoryValue.Id>();
    for (final ValueRadioButton b : approvalButtons) {
      if (b.getValue()) {
        values.put(b.value.getCategoryId(), b.value.getId());
      }
    }

    enableForm(false);
    Util.T_DETAIL_SVC.publishComments(changeSetId, message.getText().trim(),
        new HashSet<ApprovalCategoryValue.Id>(values.values()),
        new GerritCallback<VoidResult>() {
          public void onSuccess(final VoidResult result) {
            if(submit) {
              submit();
            } else {
              saveStateOnUnload = false;
              goChangeSet();
            }
          }

          @Override
          public void onFailure(Throwable caught) {
            super.onFailure(caught);
            enableForm(true);
          }
        });
  }

  private void submit() {
    Util.T_MANAGE_SVC.submit(changeSetId,
        new GerritCallback<TopicDetail>() {
          public void onSuccess(TopicDetail result) {
            saveStateOnUnload = false;
            goChangeSet();
          }

          @Override
          public void onFailure(Throwable caught) {
            goChangeSet();
            super.onFailure(caught);
          }
        });
  }

  private void goChangeSet() {
    final Topic.Id ck = changeSetId.getParentKey();
    Gerrit.display(PageLinks.toTopic(ck), new TopicScreen(ck));
  }

  private static class ValueRadioButton extends RadioButton {
    final ApprovalCategoryValue value;

    ValueRadioButton(final ApprovalCategoryValue v, final String label) {
      super(label);
      value = v;
    }
  }

  private static class SavedState {
    final ChangeSet.Id changeSetId;
    final String message;
    final Map<ApprovalCategory.Id, ApprovalCategoryValue> approvals;

    SavedState(final PublishTopicCommentScreen p) {
      changeSetId = p.changeSetId;
      message = p.message.getText();
      approvals = new HashMap<ApprovalCategory.Id, ApprovalCategoryValue>();
      for (final ValueRadioButton b : p.approvalButtons) {
        if (b.getValue()) {
          approvals.put(b.value.getCategoryId(), b.value);
        }
      }
    }
  }
}
