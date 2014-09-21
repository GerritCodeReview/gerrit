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
import com.google.gerrit.client.ui.HintTextBox;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.event.logical.shared.SelectionEvent;
import com.google.gwt.event.logical.shared.SelectionHandler;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.rpc.StatusCodeException;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.SuggestBox;
import com.google.gwt.user.client.ui.SuggestBox.DefaultSuggestionDisplay;
import com.google.gwt.user.client.ui.SuggestOracle.Suggestion;
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
  @UiField Element form;
  @UiField Element error;
  @UiField(provided = true)
  SuggestBox suggestBox;

  private ChangeScreen2.Style style;
  private Element ccText;

  private RestReviewerSuggestOracle reviewerSuggestOracle;
  private HintTextBox nameTxtBox;
  private Change.Id changeId;
  private boolean submitOnSelection;

  Reviewers() {
    reviewerSuggestOracle = new RestReviewerSuggestOracle();
    nameTxtBox = new HintTextBox();
    suggestBox = new SuggestBox(reviewerSuggestOracle, nameTxtBox);
    initWidget(uiBinder.createAndBindUi(this));

    nameTxtBox.setVisibleLength(55);
    nameTxtBox.setHintText(Util.C.approvalTableAddReviewerHint());
    nameTxtBox.addKeyDownHandler(new KeyDownHandler() {
      @Override
      public void onKeyDown(KeyDownEvent e) {
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

  void init(ChangeScreen2.Style style, Element ccText) {
    this.style = style;
    this.ccText = ccText;
  }

  void set(ChangeInfo info) {
    this.changeId = info.legacy_id();
    display(info);
    reviewerSuggestOracle.setChange(changeId);
    openForm.setVisible(Gerrit.isSignedIn());
  }

  @UiHandler("openForm")
  void onOpenForm(ClickEvent e) {
    onOpenForm();
  }

  void onOpenForm() {
    UIObject.setVisible(form, true);
    UIObject.setVisible(error, false);
    openForm.setVisible(false);
    suggestBox.setFocus(true);
  }

  @UiHandler("add")
  void onAdd(ClickEvent e) {
    String reviewer = suggestBox.getText();
    if (!reviewer.isEmpty()) {
      addReviewer(reviewer, false);
    }
  }

  @UiHandler("addme")
  void onAddMe(ClickEvent e) {
    String accountId = String.valueOf(Gerrit.getUserAccountInfo()._account_id());
    addReviewer(accountId, false);
  }

  @UiHandler("cancel")
  void onCancel(ClickEvent e) {
    openForm.setVisible(true);
    UIObject.setVisible(form, false);
    suggestBox.setFocus(false);
  }

  private void addReviewer(final String reviewer, boolean confirmed) {
    ChangeApi.reviewers(changeId.get()).post(
        PostInput.create(reviewer, confirmed),
        new GerritCallback<PostResult>() {
          public void onSuccess(PostResult result) {
            nameTxtBox.setEnabled(true);

            if (result.confirm()) {
              askForConfirmation(result.error());
            } else if (result.error() != null) {
              UIObject.setVisible(error, true);
              error.setInnerText(result.error());
            } else {
              UIObject.setVisible(error, false);
              error.setInnerText("");
              nameTxtBox.setText("");

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
            nameTxtBox.setEnabled(true);
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

    Set<Integer> removable = new HashSet<>();
    if (info.removable_reviewers() != null) {
      for (AccountInfo a : Natives.asList(info.removable_reviewers())) {
        removable.add(a._account_id());
      }
    }

    Map<Integer, VotableInfo> votable = votable(info);

    SafeHtml rHtml = Labels.formatUserList(style,
        r.values(), removable, votable);
    SafeHtml ccHtml = Labels.formatUserList(style,
        cc.values(), removable, votable);

    reviewersText.setInnerSafeHtml(rHtml);
    ccText.setInnerSafeHtml(ccHtml);
  }

  private static Map<Integer, VotableInfo> votable(ChangeInfo change) {
    Map<Integer, VotableInfo> d = new HashMap<>();
    for (String name : change.labels()) {
      LabelInfo label = change.label(name);
      if (label.all() != null) {
        for (ApprovalInfo ai : Natives.asList(label.all())) {
          int id = ai._account_id();
          VotableInfo ad = d.get(id);
          if (ad == null) {
            ad = new VotableInfo();
            d.put(id, ad);
          }
          if (ai.has_value()) {
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
