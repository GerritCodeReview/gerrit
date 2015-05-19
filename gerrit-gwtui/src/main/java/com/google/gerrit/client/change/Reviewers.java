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
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.account.AccountInfo;
import com.google.gerrit.client.changes.ChangeApi;
import com.google.gerrit.client.changes.ChangeInfo;
import com.google.gerrit.client.changes.ChangeInfo.ApprovalInfo;
import com.google.gerrit.client.changes.ChangeInfo.LabelInfo;
import com.google.gerrit.client.changes.Util;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.NativeMap;
import com.google.gerrit.client.rpc.NativeString;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.client.ui.RemoteSuggestBox;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.logical.shared.CloseEvent;
import com.google.gwt.event.logical.shared.CloseHandler;
import com.google.gwt.event.logical.shared.SelectionEvent;
import com.google.gwt.event.logical.shared.SelectionHandler;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.rpc.StatusCodeException;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.UIObject;
import com.google.gwtexpui.safehtml.client.SafeHtml;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Add reviewers. */
public class Reviewers extends Composite {
  interface Binder extends UiBinder<HTMLPanel, Reviewers> {}
  private static final Binder uiBinder = GWT.create(Binder.class);

  @UiField Element reviewersText;
  @UiField Button openForm;
  @UiField Button addMe;
  @UiField Element form;
  @UiField Element error;
  @UiField(provided = true)
  RemoteSuggestBox suggestBox;

  private ChangeScreen.Style style;
  private Element ccText;

  private ReviewerSuggestOracle reviewerSuggestOracle;
  private Change.Id changeId;

  Reviewers() {
    reviewerSuggestOracle = new ReviewerSuggestOracle();
    suggestBox = new RemoteSuggestBox(reviewerSuggestOracle);
    suggestBox.setVisibleLength(55);
    suggestBox.setHintText(Util.C.approvalTableAddReviewerHint());
    suggestBox.addCloseHandler(new CloseHandler<RemoteSuggestBox>() {
      @Override
      public void onClose(CloseEvent<RemoteSuggestBox> event) {
        Reviewers.this.onCancel(null);
      }
    });
    suggestBox.addSelectionHandler(new SelectionHandler<String>() {
      @Override
      public void onSelection(SelectionEvent<String> event) {
        addReviewer(event.getSelectedItem(), false);
      }
    });

    initWidget(uiBinder.createAndBindUi(this));
  }

  void init(ChangeScreen.Style style, Element ccText) {
    this.style = style;
    this.ccText = ccText;
  }

  void set(ChangeInfo info) {
    this.changeId = info.legacyId();
    display(info);
    reviewerSuggestOracle.setChange(changeId);
    openForm.setVisible(Gerrit.isSignedIn());
  }

  @UiHandler("openForm")
  void onOpenForm(@SuppressWarnings("unused") ClickEvent e) {
    onOpenForm();
  }

  void onOpenForm() {
    UIObject.setVisible(form, true);
    UIObject.setVisible(error, false);
    openForm.setVisible(false);
    suggestBox.setFocus(true);
  }

  @UiHandler("add")
  void onAdd(@SuppressWarnings("unused") ClickEvent e) {
    addReviewer(suggestBox.getText(), false);
  }

  @UiHandler("addMe")
  void onAddMe(@SuppressWarnings("unused") ClickEvent e) {
    String accountId = String.valueOf(Gerrit.getUserAccountInfo()._accountId());
    addReviewer(accountId, false);
  }

  @UiHandler("cancel")
  void onCancel(@SuppressWarnings("unused") ClickEvent e) {
    openForm.setVisible(true);
    UIObject.setVisible(form, false);
    suggestBox.setFocus(false);
  }

  private void addReviewer(final String reviewer, boolean confirmed) {
    if (reviewer.isEmpty()) {
      return;
    }

    ChangeApi.reviewers(changeId.get()).post(
        PostInput.create(reviewer, confirmed),
        new GerritCallback<PostResult>() {
          @Override
          public void onSuccess(PostResult result) {
            if (result.confirm()) {
              askForConfirmation(result.error());
            } else if (result.error() != null) {
              UIObject.setVisible(error, true);
              error.setInnerText(result.error());
            } else {
              UIObject.setVisible(error, false);
              error.setInnerText("");
              suggestBox.setText("");

              if (result.reviewers() != null
                  && result.reviewers().length() > 0) {
                updateReviewerList();
              }
            }
          }

          private void askForConfirmation(String text) {
            new ConfirmationDialog(
                Util.C.approvalTableAddManyReviewersConfirmationDialogTitle(),
                new SafeHtmlBuilder().append(text),
                new ConfirmationCallback() {
                  @Override
                  public void onOk() {
                    addReviewer(reviewer, true);
                  }
                }).center();
          }

          @Override
          public void onFailure(Throwable err) {
            UIObject.setVisible(error, true);
            error.setInnerText(err instanceof StatusCodeException
                ? ((StatusCodeException) err).getEncodedResponse()
                : err.getMessage());
          }
        });
  }

  private void updateReviewerList() {
    ChangeApi.detail(changeId.get(),
        new GerritCallback<ChangeInfo>() {
          @Override
          public void onSuccess(ChangeInfo result) {
            display(result);
          }
        });
  }

  private void display(ChangeInfo info) {
    Map<Integer, AccountInfo> r = new HashMap<>();
    Map<Integer, AccountInfo> cc = new HashMap<>();
    for (LabelInfo label : Natives.asList(info.allLabels().values())) {
      if (label.all() != null) {
        for (ApprovalInfo ai : Natives.asList(label.all())) {
          (ai.value() != 0 ? r : cc).put(ai._accountId(), ai);
        }
      }
    }
    for (Integer i : r.keySet()) {
      cc.remove(i);
    }
    cc.remove(info.owner()._accountId());

    Set<Integer> removable = new HashSet<>();
    if (info.removableReviewers() != null) {
      for (AccountInfo a : Natives.asList(info.removableReviewers())) {
        removable.add(a._accountId());
      }
    }

    Map<Integer, VotableInfo> votable = votable(info);

    SafeHtml rHtml = Labels.formatUserList(style,
        r.values(), removable, votable);
    SafeHtml ccHtml = Labels.formatUserList(style,
        cc.values(), removable, votable);

    reviewersText.setInnerSafeHtml(rHtml);
    ccText.setInnerSafeHtml(ccHtml);
    if (Gerrit.isSignedIn()) {
      int currentUser = Gerrit.getUserAccountInfo()._accountId();
      boolean showAddMeButton = info.owner()._accountId() != currentUser
          && !cc.containsKey(currentUser)
          && !r.containsKey(currentUser);
      addMe.setVisible(showAddMeButton);
    }
  }

  private static Map<Integer, VotableInfo> votable(ChangeInfo change) {
    Map<Integer, VotableInfo> d = new HashMap<>();
    for (String name : change.labels()) {
      LabelInfo label = change.label(name);
      if (label.all() != null) {
        for (ApprovalInfo ai : Natives.asList(label.all())) {
          int id = ai._accountId();
          VotableInfo ad = d.get(id);
          if (ad == null) {
            ad = new VotableInfo();
            d.put(id, ad);
          }
          if (ai.hasValue()) {
            ad.votable(name);
          }
        }
      }
    }
    return d;
  }


  public static class PostInput extends JavaScriptObject {
    public static PostInput create(String reviewer, boolean confirmed) {
      PostInput input = createObject().cast();
      input.init(reviewer, confirmed);
      return input;
    }

    private native void init(String reviewer, boolean confirmed) /*-{
      this.reviewer = reviewer;
      if (confirmed) {
        this.confirmed = true;
      }
    }-*/;

    protected PostInput() {
    }
  }

  public static class ReviewerInfo extends AccountInfo {
    final Set<String> approvals() {
      return Natives.keys(_approvals());
    }
    final native String approval(String l) /*-{ return this.approvals[l]; }-*/;
    private final native NativeMap<NativeString> _approvals() /*-{ return this.approvals; }-*/;

    protected ReviewerInfo() {
    }
  }

  public static class PostResult extends JavaScriptObject {
    public final native JsArray<ReviewerInfo> reviewers() /*-{ return this.reviewers; }-*/;
    public final native boolean confirm() /*-{ return this.confirm || false; }-*/;
    public final native String error() /*-{ return this.error; }-*/;

    protected PostResult() {
    }
  }
}
