// Copyright 2009 Google Inc.
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
import com.google.gerrit.client.patches.PatchUtil;
import com.google.gerrit.client.reviewdb.ApprovalCategory;
import com.google.gerrit.client.reviewdb.ApprovalCategoryValue;
import com.google.gerrit.client.reviewdb.Change;
import com.google.gerrit.client.reviewdb.ChangeApproval;
import com.google.gerrit.client.reviewdb.PatchLineComment;
import com.google.gerrit.client.reviewdb.PatchSet;
import com.google.gerrit.client.rpc.Common;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.AccountScreen;
import com.google.gerrit.client.ui.PatchLink;
import com.google.gerrit.client.ui.SmallHeading;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.ClickListener;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.FormPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.RadioButton;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtjsonrpc.client.VoidResult;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PublishCommentScreen extends AccountScreen {
  private final PatchSet.Id patchSetId;
  private Collection<RadioButton> approvalButtons;
  private ChangeDescriptionBlock descBlock;
  private Panel approvalPanel;
  private TextArea message;
  private Panel draftsPanel;
  private Button send;

  public PublishCommentScreen(final PatchSet.Id psi) {
    super(Util.M.publishComments(psi.getParentKey().get(), psi.get()));
    addStyleName("gerrit-PublishCommentsScreen");
    patchSetId = psi;
  }

  @Override
  public void onLoad() {
    if (message == null) {
      approvalButtons = new ArrayList<RadioButton>();
      descBlock = new ChangeDescriptionBlock();
      add(descBlock);

      final FormPanel form = new FormPanel();
      final FlowPanel body = new FlowPanel();
      form.setWidget(body);
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
      send.addClickListener(new ClickListener() {
        public void onClick(Widget sender) {
          onSend();
        }
      });
      buttonRow.add(send);

      final Button cancel = new Button(Util.C.buttonPublishCommentsCancel());
      cancel.addClickListener(new ClickListener() {
        public void onClick(Widget sender) {
          goChange();
        }
      });
      buttonRow.add(cancel);
    }

    super.onLoad();
    message.setFocus(true);

    send.setEnabled(false);
    Util.DETAIL_SVC.patchSetPublishDetail(patchSetId,
        new GerritCallback<PatchSetPublishDetail>() {
          public void onSuccess(final PatchSetPublishDetail result) {
            send.setEnabled(true);
            display(result);
          }
        });
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

      final RadioButton b = new RadioButton(ct.getCategory().getName());
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
      b.setChecked(prior != null ? buttonValue.getValue() == prior.getValue()
          : buttonValue.getValue() == 0);
      setValue(b.getElement(), buttonValue);
      approvalButtons.add(b);
      vp.add(b);
    }
    body.add(vp);
  }

  private void display(final PatchSetPublishDetail r) {
    descBlock.display(r.getChange(), r.getPatchSetInfo(), r.getAccounts());

    approvalPanel.clear();
    approvalButtons.clear();
    if (r.getChange().getStatus().isOpen()) {
      initApprovals(r, approvalPanel);
    }

    draftsPanel.clear();
    if (!r.getDrafts().isEmpty()) {
      draftsPanel.add(new SmallHeading(Util.C.headingPatchComments()));

      Panel panel = null;
      String priorFile = "";
      for (final PatchLineComment c : r.getDrafts()) {
        final String fn = c.getKey().getParentKey().get();
        if (!fn.equals(priorFile)) {
          panel = new FlowPanel();
          panel.addStyleName("gerrit-PatchComments");
          draftsPanel.add(panel);

          panel.add(new PatchLink.SideBySide(fn, c.getKey().getParentKey()));
          priorFile = fn;
        }

        Label m;

        m = new Label(Util.M.lineHeader(c.getLine()));
        m.setStyleName("gerrit-LineHeader");
        panel.add(m);

        m = new Label(c.getMessage());
        m.setStyleName("gerrit-LineComment");
        panel.add(m);
      }
    }
  }

  private void onSend() {
    final Map<ApprovalCategory.Id, ApprovalCategoryValue.Id> values =
        new HashMap<ApprovalCategory.Id, ApprovalCategoryValue.Id>();
    for (final RadioButton b : approvalButtons) {
      if (b.isChecked()) {
        final ApprovalCategoryValue v = getValue(b.getElement());
        if (v.getValue() != 0) {
          values.put(v.getCategoryId(), v.getId());
        }
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

  private static native void setValue(Element rbutton, ApprovalCategoryValue val)
  /*-{ rbutton["__gerritValue"] = val; }-*/;

  private static native ApprovalCategoryValue getValue(Element rbutton)
  /*-{ return rbutton["__gerritValue"]; }-*/;
}
