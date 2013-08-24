// Copyright (C) 2013 The Android Open Source Project
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

import com.google.gerrit.client.ConfirmationCallback;
import com.google.gerrit.client.ConfirmationDialog;
import com.google.gerrit.client.ErrorDialog;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.account.AccountInfo;
import com.google.gerrit.client.change.ChangeScreen2.Style;
import com.google.gerrit.client.changes.ApprovalTable.PostInput;
import com.google.gerrit.client.changes.ApprovalTable.PostResult;
import com.google.gerrit.client.changes.ChangeApi;
import com.google.gerrit.client.changes.ChangeInfo;
import com.google.gerrit.client.changes.ChangeInfo.ApprovalInfo;
import com.google.gerrit.client.changes.ChangeInfo.LabelInfo;
import com.google.gerrit.client.changes.Util;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.client.ui.HintTextBox;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Change.Id;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.event.logical.shared.SelectionEvent;
import com.google.gwt.event.logical.shared.SelectionHandler;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.SuggestBox;
import com.google.gwt.user.client.ui.SuggestBox.DefaultSuggestionDisplay;
import com.google.gwt.user.client.ui.SuggestOracle.Suggestion;
import com.google.gwt.user.client.ui.UIObject;
import com.google.gwtexpui.safehtml.client.SafeHtml;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;

import java.util.HashMap;
import java.util.Map;

/** Add reviewers. */
class Reviewers extends Composite {
  interface Binder extends UiBinder<HTMLPanel, Reviewers> {}
  private static final Binder uiBinder = GWT.create(Binder.class);

  @UiField Image edit;
  @UiField Element reviewers;
  @UiField Element form;
  @UiField(provided = true)
  SuggestBox suggestBox;

  private RestReviewerSuggestOracle reviewerSuggestOracle;
  private HintTextBox nameTxtBox;
  private Id changeId;
  private Style style;
  private boolean submitOnSelection;

  Reviewers() {
    reviewerSuggestOracle = new RestReviewerSuggestOracle();
    nameTxtBox = new HintTextBox();
    suggestBox = new SuggestBox(reviewerSuggestOracle, nameTxtBox);
    initWidget(uiBinder.createAndBindUi(this));
    nameTxtBox.setVisibleLength(55);
    nameTxtBox.setHintText(Util.C.approvalTableAddReviewerHint());
    nameTxtBox.addKeyPressHandler(new KeyPressHandler() {
      @Override
      public void onKeyPress(KeyPressEvent e) {
        submitOnSelection = false;

        if (e.getNativeEvent().getKeyCode() == KeyCodes.KEY_ESCAPE) {
          onCancel(null);
        } else if (e.getNativeEvent().getKeyCode() == KeyCodes.KEY_ENTER) {
          if (((DefaultSuggestionDisplay) suggestBox.getSuggestionDisplay())
              .isSuggestionListShowing()) {
            submitOnSelection = true;
          } else {
            onAdd(null);
          }
        }
      }
    });
    suggestBox.addSelectionHandler(new SelectionHandler<Suggestion>() {
      @Override
      public void onSelection(SelectionEvent<Suggestion> event) {
        nameTxtBox.setFocus(true);
        if (submitOnSelection) {
          onAdd(null);
        }
      }
    });
  }

  void set(Change.Id changeId) {
    this.changeId = changeId;
    reviewerSuggestOracle.setChange(changeId);
  }

  void init(Style style) {
    this.style = style;
    edit.setVisible(Gerrit.isSignedIn());
  }

  void setReviewers(SafeHtml formatUserList) {
    reviewers.setInnerSafeHtml(formatUserList);
  }

  @UiHandler("edit")
  void onEdit(ClickEvent e) {
    onEdit();
  }

  void onEdit() {
    UIObject.setVisible(form, true);
    suggestBox.setFocus(true);
  }

  @UiHandler("add")
  void onAdd(ClickEvent e) {
    String reviewer = suggestBox.getText();
    if (!reviewer.isEmpty()) {
      addReviewer(reviewer, false);
    }
  }

  @UiHandler("cancel")
  void onCancel(ClickEvent e) {
    UIObject.setVisible(form, false);
    suggestBox.setFocus(false);
  }

  private void addReviewer(final String reviewer, boolean confirmed) {
    ChangeApi.reviewers(changeId.get()).post(
        PostInput.create(reviewer, confirmed),
        new GerritCallback<PostResult>() {
          public void onSuccess(PostResult result) {
            nameTxtBox.setEnabled(true);
            nameTxtBox.setText("");
            if (result.error() == null) {
              reloadReviewers();
            } else if (result.confirm()) {
              askForConfirmation(result.error());
            } else {
              new ErrorDialog(new SafeHtmlBuilder().append(result.error()));
            }
          }

          private void askForConfirmation(String text) {
            String title = Util.C
                .approvalTableAddManyReviewersConfirmationDialogTitle();
            ConfirmationDialog confirmationDialog = new ConfirmationDialog(
                title, new SafeHtmlBuilder().append(text),
                new ConfirmationCallback() {
                  @Override
                  public void onOk() {
                    addReviewer(reviewer, true);
                  }
                });
            confirmationDialog.center();
          }

          @Override
          public void onFailure(final Throwable caught) {
            nameTxtBox.setEnabled(true);
            if (isNoSuchEntity(caught)) {
              new ErrorDialog(Util.M.reviewerNotFound(reviewer)).center();
            } else {
              super.onFailure(caught);
            }
          }
        });
  }

  private void reloadReviewers() {
    ChangeApi.detail(changeId.get(),
        new GerritCallback<ChangeInfo>() {
          @Override
          public void onSuccess(ChangeInfo result) {
            display(result);
          }
        });
  }

  private void display(ChangeInfo info) {
    Map<Integer, AccountInfo> r = new HashMap<Integer, AccountInfo>();
    Map<Integer, AccountInfo> cc = new HashMap<Integer, AccountInfo>();
    for (LabelInfo label : Natives.asList(info.all_labels().values())) {
      if (label.all() != null) {
        for (ApprovalInfo ai : Natives.asList(label.all())) {
          (ai.value() != 0 ? r : cc).put(ai._account_id(), ai);
        }
      }
    }
    for (Integer i : r.keySet()) {
      cc.remove(i);
    }
    cc.remove(info.owner()._account_id());
    setReviewers(Labels.formatUserList(style, cc.values()));
  }
}
