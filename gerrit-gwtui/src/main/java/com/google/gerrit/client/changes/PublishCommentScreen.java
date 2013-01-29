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
import com.google.gerrit.client.patches.AbstractPatchContentTable;
import com.google.gerrit.client.patches.CommentEditorContainer;
import com.google.gerrit.client.patches.CommentEditorPanel;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.AccountScreen;
import com.google.gerrit.client.ui.CommentLinkProcessor;
import com.google.gerrit.client.ui.PatchLink;
import com.google.gerrit.client.ui.SmallHeading;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.common.data.ApprovalType;
import com.google.gerrit.common.data.ApprovalTypes;
import com.google.gerrit.common.data.PatchSetPublishDetail;
import com.google.gerrit.common.data.PermissionRange;
import com.google.gerrit.reviewdb.client.ApprovalCategory;
import com.google.gerrit.reviewdb.client.ApprovalCategoryValue;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gwt.core.client.JavaScriptObject;
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
import com.google.gwtexpui.safehtml.client.SafeHtml;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;
import com.google.gwtjsonrpc.common.VoidResult;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PublishCommentScreen extends AccountScreen implements
    ClickHandler, CommentEditorContainer {
  private static SavedState lastState;

  private final PatchSet.Id patchSetId;
  private String revision;
  private Collection<ValueRadioButton> approvalButtons;
  private ChangeDescriptionBlock descBlock;
  private ApprovalTable approvals;
  private Panel approvalPanel;
  private NpTextArea message;
  private FlowPanel draftsPanel;
  private Button send;
  private Button submit;
  private Button cancel;
  private boolean saveStateOnUnload = true;
  private List<CommentEditorPanel> commentEditors;

  public PublishCommentScreen(final PatchSet.Id psi) {
    patchSetId = psi;
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();
    addStyleName(Gerrit.RESOURCES.css().publishCommentsScreen());

    approvalButtons = new ArrayList<ValueRadioButton>();
    descBlock = new ChangeDescriptionBlock(null);
    add(descBlock);

    approvals = new ApprovalTable();
    add(approvals);

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

    draftsPanel = new FlowPanel();
    body.add(draftsPanel);

    final FlowPanel buttonRow = new FlowPanel();
    buttonRow.setStyleName(Gerrit.RESOURCES.css().patchSetActions());
    body.add(buttonRow);

    send = new Button(Util.C.buttonPublishCommentsSend());
    send.addClickHandler(this);
    buttonRow.add(send);

    submit = new Button(Util.C.buttonPublishSubmitSend());
    submit.addClickHandler(this);
    buttonRow.add(submit);

    cancel = new Button(Util.C.buttonPublishCommentsCancel());
    cancel.addClickHandler(this);
    buttonRow.add(cancel);
  }

  private void enableForm(final boolean enabled) {
    for (final ValueRadioButton approvalButton : approvalButtons) {
      approvalButton.setEnabled(enabled);
    }
    message.setEnabled(enabled);
    for (final CommentEditorPanel commentEditor : commentEditors) {
      commentEditor.enableButtons(enabled);
    }
    send.setEnabled(enabled);
    submit.setEnabled(enabled);
    cancel.setEnabled(enabled);
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
      goChange();
    }
  }

  @Override
  public void notifyDraftDelta(int delta) {
  }

  @Override
  public void remove(CommentEditorPanel editor) {
    commentEditors.remove(editor);

    // The editor should be embedded into a panel holding all
    // editors for the same file.
    //
    FlowPanel parent = (FlowPanel) editor.getParent();
    parent.remove(editor);

    // If the panel now holds no editors, remove it.
    //
    int editorCount = 0;
    for (Widget w : parent) {
      if (w instanceof CommentEditorPanel) {
        editorCount++;
      }
    }
    if (editorCount == 0) {
      parent.removeFromParent();
    }

    // If that was the last file with a draft, remove the heading.
    //
    if (draftsPanel.getWidgetCount() == 1) {
      draftsPanel.clear();
    }
  }

  private void initMessage(final Panel body) {
    body.add(new SmallHeading(Util.C.headingCoverMessage()));

    final VerticalPanel mwrap = new VerticalPanel();
    mwrap.setStyleName(Gerrit.RESOURCES.css().coverMessage());
    body.add(mwrap);

    message = new NpTextArea();
    message.setCharacterWidth(60);
    message.setVisibleLines(10);
    message.setSpellCheck(true);
    mwrap.add(message);
  }

  private void initApprovals(final PatchSetPublishDetail r, final Panel body) {
    ApprovalTypes types = Gerrit.getConfig().getApprovalTypes();
    for (PermissionRange range : r.getLabels()) {
      ApprovalType type = types.byLabel(range.getLabel());
      if (type != null) {
        // Legacy type, use radio buttons.
        initApprovalType(r, body, type, range);
      } else {
        // TODO Newer style label.
      }
    }
  }

  private void initApprovalType(final PatchSetPublishDetail r,
      final Panel body, final ApprovalType ct, final PermissionRange range) {
    body.add(new SmallHeading(ct.getCategory().getName() + ":"));

    final VerticalPanel vp = new VerticalPanel();
    vp.setStyleName(Gerrit.RESOURCES.css().approvalCategoryList());
    final List<ApprovalCategoryValue> lst =
        new ArrayList<ApprovalCategoryValue>(ct.getValues());
    Collections.reverse(lst);
    final ApprovalCategory.Id catId = ct.getCategory().getId();
    final PatchSetApproval prior = r.getChangeApproval(catId);

    for (final ApprovalCategoryValue buttonValue : lst) {
      if (!range.contains(buttonValue.getValue())) {
        continue;
      }

      ValueRadioButton b = new ValueRadioButton(ct.getCategory(), buttonValue);
      SafeHtml buf = new SafeHtmlBuilder().append(buttonValue.format());
      buf = CommentLinkProcessor.apply(buf);
      SafeHtml.set(b, buf);

      if (lastState != null && patchSetId.equals(lastState.patchSetId)
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

  private void display(final PatchSetPublishDetail r) {
    setPageTitle(Util.M.publishComments(r.getChange().getKey().abbreviate(),
        patchSetId.get()));
    descBlock.display(r.getChange(), null, false, r.getPatchSetInfo(), r.getAccounts(),
        r.getSubmitTypeRecord());

    if (r.getChange().getStatus().isOpen()) {
      initApprovals(r, approvalPanel);

      approvals.setAccountInfoCache(r.getAccounts());
      approvals.display(r);
    } else {
      approvals.setVisible(false);
    }

    if (lastState != null && patchSetId.equals(lastState.patchSetId)) {
      message.setText(lastState.message);
    }

    draftsPanel.clear();
    commentEditors = new ArrayList<CommentEditorPanel>();
    revision = r.getPatchSetInfo().getRevId();

    if (!r.getDrafts().isEmpty()) {
      draftsPanel.add(new SmallHeading(Util.C.headingPatchComments()));

      Panel panel = null;
      String priorFile = "";
      for (final PatchLineComment c : r.getDrafts()) {
        final Patch.Key patchKey = c.getKey().getParentKey();
        final String fn = patchKey.get();
        if (!fn.equals(priorFile)) {
          panel = new FlowPanel();
          panel.addStyleName(Gerrit.RESOURCES.css().patchComments());
          draftsPanel.add(panel);
          // Parent table can be null here since we are not showing any
          // next/previous links
          panel.add(new PatchLink.SideBySide(
              PatchTable.getDisplayFileName(patchKey), null, patchKey, 0, null, null));
          priorFile = fn;
        }

        final CommentEditorPanel editor = new CommentEditorPanel(c);
        if (c.getLine() == AbstractPatchContentTable.R_HEAD) {
          editor.setAuthorNameText(Util.C.fileCommentHeader());
        } else {
          editor.setAuthorNameText(Util.M.lineHeader(c.getLine()));
        }
        editor.setOpen(true);
        commentEditors.add(editor);
        panel.add(editor);
      }
    }

    submit.setVisible(r.canSubmit());
    if (Gerrit.getConfig().testChangeMerge()) {
      submit.setEnabled(r.getChange().isMergeable());
    }
  }

  private void onSend(final boolean submit) {
    if (commentEditors.isEmpty()) {
      onSend2(submit);
    } else {
      final GerritCallback<VoidResult> afterSaveDraft =
          new GerritCallback<VoidResult>() {
            private int done;

            @Override
            public void onSuccess(final VoidResult result) {
              if (++done == commentEditors.size()) {
                onSend2(submit);
              }
            }
          };
      for (final CommentEditorPanel p : commentEditors) {
        p.saveDraft(afterSaveDraft);
      }
    }
  }

  private void onSend2(final boolean submit) {
    ReviewInput data = ReviewInput.create();
    data.message(ChangeApi.emptyToNull(message.getText().trim()));
    data.init();
    for (final ValueRadioButton b : approvalButtons) {
      if (b.getValue()) {
        data.label(b.category.getLabelName(), b.value.getValue());
      }
    }

    enableForm(false);
    new RestApi("/changes/")
      .id(String.valueOf(patchSetId.getParentKey().get()))
      .view("revisions").id(revision).view("review")
      .data(data)
      .post(new GerritCallback<ReviewInput>() {
          @Override
          public void onSuccess(ReviewInput result) {
            if (submit) {
              submit();
            } else {
              saveStateOnUnload = false;
              goChange();
            }
          }

          @Override
          public void onFailure(Throwable caught) {
            super.onFailure(caught);
            enableForm(true);
          }
        });
  }

  private static class ReviewInput extends JavaScriptObject {
    static ReviewInput create() {
      return (ReviewInput) createObject();
    }

    final native void message(String m) /*-{ if(m)this.message=m; }-*/;
    final native void label(String n, short v) /*-{ this.labels[n]=v; }-*/;
    final native void init() /*-{
      this.labels = {};
      this.strict_labels = true;
      this.drafts = 'PUBLISH';
    }-*/;

    protected ReviewInput() {
    }
  }

  private void submit() {
    ChangeApi.submit(patchSetId.getParentKey().get(), revision,
      new GerritCallback<SubmitInfo>() {
          public void onSuccess(SubmitInfo result) {
            saveStateOnUnload = false;
            goChange();
          }

          @Override
          public void onFailure(Throwable err) {
            if (SubmitFailureDialog.isConflict(err)) {
              new SubmitFailureDialog(err.getMessage()).center();
            } else {
              super.onFailure(err);
            }
            goChange();
          }
        });
  }

  private void goChange() {
    final Change.Id ck = patchSetId.getParentKey();
    Gerrit.display(PageLinks.toChange(ck), new ChangeScreen(ck));
  }

  private static class ValueRadioButton extends RadioButton {
    final ApprovalCategory category;
    final ApprovalCategoryValue value;

    ValueRadioButton(ApprovalCategory c, ApprovalCategoryValue v) {
      super(c.getLabelName());
      category = c;
      value = v;
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
        if (b.getValue()) {
          approvals.put(b.value.getCategoryId(), b.value);
        }
      }
    }
  }
}
