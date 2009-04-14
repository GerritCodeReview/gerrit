// Copyright (C) 2009 The Android Open Source Project
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
import com.google.gerrit.client.Link;
import com.google.gerrit.client.data.ApprovalType;
import com.google.gerrit.client.patches.LineCommentPanel;
import com.google.gerrit.client.patches.PatchUtil;
import com.google.gerrit.client.reviewdb.ApprovalCategory;
import com.google.gerrit.client.reviewdb.ApprovalCategoryValue;
import com.google.gerrit.client.reviewdb.Change;
import com.google.gerrit.client.reviewdb.ChangeApproval;
import com.google.gerrit.client.reviewdb.Patch;
import com.google.gerrit.client.reviewdb.PatchLineComment;
import com.google.gerrit.client.reviewdb.PatchSet;
import com.google.gerrit.client.rpc.Common;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.AccountScreen;
import com.google.gerrit.client.ui.PatchLink;
import com.google.gerrit.client.ui.SmallHeading;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.ClickListener;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.FormHandler;
import com.google.gwt.user.client.ui.FormPanel;
import com.google.gwt.user.client.ui.FormSubmitCompleteEvent;
import com.google.gwt.user.client.ui.FormSubmitEvent;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.RadioButton;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtexpui.safehtml.client.SafeHtml;
import com.google.gwtjsonrpc.client.VoidResult;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PublishCommentScreen extends AccountScreen implements
    ClickListener {
  private static SavedState lastState;

  private final PatchSet.Id patchSetId;
  private Collection<ValueRadioButton> approvalButtons;
  private ChangeDescriptionBlock descBlock;
  private Panel approvalPanel;
  private TextArea message;
  private Panel draftsPanel;
  private Button send;
  private Button cancel;
  private boolean saveStateOnUnload = true;

  public PublishCommentScreen(final PatchSet.Id psi) {
    patchSetId = psi;
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();
    addStyleName("gerrit-PublishCommentsScreen");
    setPageTitle(Util.M.publishComments(patchSetId.getParentKey().get(),
        patchSetId.get()));

    approvalButtons = new ArrayList<ValueRadioButton>();
    descBlock = new ChangeDescriptionBlock();
    add(descBlock);

    final FormPanel form = new FormPanel();
    final FlowPanel body = new FlowPanel();
    form.setWidget(body);
    form.addFormHandler(new FormHandler() {
      public void onSubmit(FormSubmitEvent event) {
        event.setCancelled(true);
      }

      public void onSubmitComplete(FormSubmitCompleteEvent event) {
      }
    });
    add(form);

    approvalPanel = new FlowPanel();
    body.add(approvalPanel);
    initMessage(body);

    draftsPanel = new FlowPanel();
    body.add(draftsPanel);

    final FlowPanel buttonRow = new FlowPanel();
    buttonRow.setStyleName("gerrit-CommentEditor-Buttons");
    body.add(buttonRow);

    send = new Button(Util.C.buttonPublishCommentsSend());
    send.addClickListener(this);
    buttonRow.add(send);

    cancel = new Button(Util.C.buttonPublishCommentsCancel());
    cancel.addClickListener(this);
    buttonRow.add(cancel);
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    Util.DETAIL_SVC.patchSetPublishDetail(patchSetId,
        new ScreenLoadCallback<PatchSetPublishDetail>(this) {
          @Override
          protected void preDisplay(final PatchSetPublishDetail result) {
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
    if (saveStateOnUnload) {
      lastState = new SavedState(this);
    }
  }

  public void onClick(final Widget sender) {
    if (send == sender) {
      lastState = null;
      onSend();
    } else if (cancel == sender) {
      lastState = null;
      saveStateOnUnload = false;
      goChange();
    }
  }

  private void initMessage(final Panel body) {
    body.add(new SmallHeading(Util.C.headingCoverMessage()));

    final VerticalPanel mwrap = new VerticalPanel();
    mwrap.setStyleName("gerrit-CoverMessage");
    body.add(mwrap);

    message = new TextArea();
    message.setCharacterWidth(60);
    message.setVisibleLines(10);
    DOM.setElementPropertyBoolean(message.getElement(), "spellcheck", true);
    mwrap.add(message);
  }

  private void initApprovals(final PatchSetPublishDetail r, final Panel body) {
    for (final ApprovalType ct : Common.getGerritConfig().getApprovalTypes()) {
      if (r.isAllowed(ct.getCategory().getId())) {
        initApprovalType(r, body, ct);
      }
    }
  }

  private void initApprovalType(final PatchSetPublishDetail r,
      final Panel body, final ApprovalType ct) {
    body.add(new SmallHeading(ct.getCategory().getName() + ":"));

    final VerticalPanel vp = new VerticalPanel();
    vp.setStyleName("gerrit-ApprovalCategoryList");
    final List<ApprovalCategoryValue> lst =
        new ArrayList<ApprovalCategoryValue>(ct.getValues());
    Collections.reverse(lst);
    final ApprovalCategory.Id catId = ct.getCategory().getId();
    final Set<ApprovalCategoryValue.Id> allowed = r.getAllowed(catId);
    final ChangeApproval prior = r.getChangeApproval(catId);

    for (final ApprovalCategoryValue buttonValue : lst) {
      if (!allowed.contains(buttonValue.getId())) {
        continue;
      }

      final ValueRadioButton b =
          new ValueRadioButton(buttonValue, ct.getCategory().getName());
      final StringBuilder m = new StringBuilder();
      if (buttonValue.getValue() == 0) {
        m.append(' ');
      } else if (buttonValue.getValue() > 0) {
        m.append('+');
      }
      m.append(buttonValue.getValue());
      m.append(' ');
      m.append(buttonValue.getName());
      b.setText(m.toString());

      if (lastState != null && patchSetId.equals(lastState.patchSetId)
          && lastState.approvals.containsKey(buttonValue.getCategoryId())) {
        b.setChecked(lastState.approvals.get(buttonValue.getCategoryId())
            .equals(buttonValue));
      } else {
        b.setChecked(prior != null ? buttonValue.getValue() == prior.getValue()
            : buttonValue.getValue() == 0);
      }

      approvalButtons.add(b);
      vp.add(b);
    }
    body.add(vp);
  }

  private void display(final PatchSetPublishDetail r) {
    descBlock.display(r.getChange(), r.getPatchSetInfo(), r.getAccounts());

    if (r.getChange().getStatus().isOpen()) {
      initApprovals(r, approvalPanel);
    }
    if (lastState != null && patchSetId.equals(lastState.patchSetId)) {
      message.setText(lastState.message);
    }

    draftsPanel.clear();
    if (!r.getDrafts().isEmpty()) {
      draftsPanel.add(new SmallHeading(Util.C.headingPatchComments()));

      Panel panel = null;
      String priorFile = "";
      for (final PatchLineComment c : r.getDrafts()) {
        final Patch.Key patchKey = c.getKey().getParentKey();
        final String fn = patchKey.get();
        if (!fn.equals(priorFile)) {
          panel = new FlowPanel();
          panel.addStyleName("gerrit-PatchComments");
          draftsPanel.add(panel);

          panel.add(new PatchLink.SideBySide(fn, patchKey));
          priorFile = fn;
        }

        Label m;

        m = new DoubleClickLinkLabel(patchKey);
        m.setText(Util.M.lineHeader(c.getLine()));
        m.setStyleName("gerrit-LineHeader");
        panel.add(m);

        m = new DoubleClickLinkLabel(patchKey);
        SafeHtml.set(m.getElement(), LineCommentPanel.toSafeHtml(c));
        m.setStyleName("gerrit-PatchLineComment");
        panel.add(m);
      }
    }
  }

  private void onSend() {
    final Map<ApprovalCategory.Id, ApprovalCategoryValue.Id> values =
        new HashMap<ApprovalCategory.Id, ApprovalCategoryValue.Id>();
    for (final ValueRadioButton b : approvalButtons) {
      if (b.isChecked()) {
        values.put(b.value.getCategoryId(), b.value.getId());
      }
    }

    PatchUtil.DETAIL_SVC.publishComments(patchSetId, message.getText().trim(),
        new HashSet<ApprovalCategoryValue.Id>(values.values()),
        new GerritCallback<VoidResult>() {
          public void onSuccess(final VoidResult result) {
            goChange();
          }
        });
  }

  private void goChange() {
    final Change.Id ck = patchSetId.getParentKey();
    Gerrit.display(Link.toChange(ck), new ChangeScreen(ck));
  }

  private static class ValueRadioButton extends RadioButton {
    final ApprovalCategoryValue value;

    ValueRadioButton(final ApprovalCategoryValue v, final String label) {
      super(label);
      value = v;
    }
  }

  private static class DoubleClickLinkLabel extends Label {
    private final Patch.Key patchKey;

    DoubleClickLinkLabel(final Patch.Key p) {
      patchKey = p;
      sinkEvents(Event.ONDBLCLICK);
    }

    @Override
    public void onBrowserEvent(final Event event) {
      switch (DOM.eventGetType(event)) {
        case Event.ONDBLCLICK:
          History.newItem(Link.toPatchSideBySide(patchKey), true);
          break;
      }
      super.onBrowserEvent(event);
    }
  }

  private static class SavedState {
    final PatchSet.Id patchSetId;
    final String message;
    final Map<ApprovalCategory.Id, ApprovalCategoryValue> approvals;

    SavedState(final PublishCommentScreen p) {
      patchSetId = p.patchSetId;
      message = p.message.getText();
      approvals = new HashMap<ApprovalCategory.Id, ApprovalCategoryValue>();
      for (final ValueRadioButton b : p.approvalButtons) {
        if (b.isChecked()) {
          approvals.put(b.value.getCategoryId(), b.value);
        }
      }
    }
  }
}
