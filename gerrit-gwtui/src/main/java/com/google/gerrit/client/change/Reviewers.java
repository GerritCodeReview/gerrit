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

import static com.google.gerrit.common.data.LabelValue.formatValue;

import com.google.gerrit.client.ConfirmationCallback;
import com.google.gerrit.client.ConfirmationDialog;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.account.AccountInfo;
import com.google.gerrit.client.changes.ApprovalTable.PostInput;
import com.google.gerrit.client.changes.ApprovalTable.PostResult;
import com.google.gerrit.client.changes.ChangeApi;
import com.google.gerrit.client.changes.ChangeInfo;
import com.google.gerrit.client.changes.ChangeInfo.ApprovalInfo;
import com.google.gerrit.client.changes.ChangeInfo.LabelInfo;
import com.google.gerrit.client.changes.ChangeList;
import com.google.gerrit.client.changes.Util;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gerrit.client.ui.AccountLinkPanel;
import com.google.gerrit.client.ui.HintTextBox;
import com.google.gerrit.common.changes.ListChangesOption;
import com.google.gerrit.common.data.ApprovalDetail;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.event.logical.shared.SelectionEvent;
import com.google.gwt.event.logical.shared.SelectionHandler;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.rpc.StatusCodeException;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.PushButton;
import com.google.gwt.user.client.ui.SuggestBox;
import com.google.gwt.user.client.ui.SuggestBox.DefaultSuggestionDisplay;
import com.google.gwt.user.client.ui.SuggestOracle.Suggestion;
import com.google.gwt.user.client.ui.UIObject;
import com.google.gwtexpui.safehtml.client.SafeHtml;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Add reviewers. */
class Reviewers extends Composite {
  interface Binder extends UiBinder<HTMLPanel, Reviewers> {}
  private static final Binder uiBinder = GWT.create(Binder.class);
  static final ReviewersResources R = GWT
      .create(ReviewersResources.class);

  interface ReviewersResources extends ClientBundle {
    @Source("reviewers.css")
    ReviewersCss css();
  }

  interface ReviewersCss extends CssResource {
    String reviewersTable2();
    String rightmost2();
    String header2();
    String posscore2();
    String negscore2();
    String approvalhint2();
    String notVotable2();
    String approvalscore2();
    String approvalrole2();
    String removeReviewer2();
    String link2();
    String removeReviewerCell2();
  }

  @UiField Image openForm;
  @UiField Element ccReviewers;
  @UiField Element form;
  @UiField Element error;
  @UiField(provided = true)
  SuggestBox suggestBox;
  @UiField Grid table;

  // References to UiFields from ChangeScreen2
  Labels labels;
  Element reviewers;

  private RestReviewerSuggestOracle reviewerSuggestOracle;
  private HintTextBox nameTxtBox;
  private ChangeScreen2.Style style;
  private ChangeInfo info;
  private String revision;
  private boolean submitOnSelection;

  Reviewers() {
    reviewerSuggestOracle = new RestReviewerSuggestOracle();
    nameTxtBox = new HintTextBox();
    suggestBox = new SuggestBox(reviewerSuggestOracle, nameTxtBox);
    initWidget(uiBinder.createAndBindUi(this));
    table.addStyleName(R.css().reviewersTable2());
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

  @Override
  protected void onLoad() {
    super.onLoad();
    R.css().ensureInjected();
  }

  void set(ChangeInfo info, String revision,
      Labels labels, Element reviewers) {
    this.info = info;
    this.revision = revision;
    this.labels = labels;
    this.reviewers = reviewers;
    reviewerSuggestOracle.setChange(info.legacy_id());
    renderReviewers(info);
  }

  void init(ChangeScreen2.Style style) {
    this.style = style;
    openForm.setVisible(Gerrit.isSignedIn());
  }

  void setCcReviewers(SafeHtml formatUserList) {
    ccReviewers.setInnerSafeHtml(formatUserList);
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
    renderRemoveReviewers();
  }

  private void renderRemoveReviewers() {
    Map<Integer, ApprovalDetail> byUser =
        new LinkedHashMap<Integer, ApprovalDetail>();
    Map<Integer, AccountInfo> accounts =
        new LinkedHashMap<Integer, AccountInfo>();
    initLabels(accounts, byUser);
    table.clear();
    if (byUser.isEmpty()) {
      table.setVisible(false);
    } else {
      table.resize(1, 3);
      List<String> labels = new ArrayList<String>(info.labels());
      Collections.sort(labels);
      displayHeader(labels);
      table.resizeRows(1 + byUser.size());
      int i = 1;
      for (ApprovalDetail ad : ApprovalDetail.sort(
          byUser.values(), info.owner()._account_id())) {
        displayRow(i++, ad, labels, accounts.get(ad.getAccount().get()));
      }
      table.setVisible(true);
    }
  }

  private List<String> initLabels(
      Map<Integer, AccountInfo> accounts,
      Map<Integer, ApprovalDetail> byUser) {
    Set<Integer> removableReviewers = removableReviewers();
    List<String> missing = new ArrayList<String>();
    for (String name : info.labels()) {
      LabelInfo label = info.label(name);

      String min = null;
      String max = null;
      for (String v : label.values()) {
        if (min == null) {
          min = v;
        }
        if (v.startsWith("+")) {
          max = v;
        }
      }

      if (label.status() == SubmitRecord.Label.Status.NEED) {
        missing.add(name);
      }

      if (label.all() != null) {
        for (ApprovalInfo ai : Natives.asList(label.all())) {
          if (!accounts.containsKey(ai._account_id())) {
            accounts.put(ai._account_id(), ai);
          }
          int id = ai._account_id();
          ApprovalDetail ad = byUser.get(id);
          if (ad == null) {
            ad = new ApprovalDetail(new Account.Id(id));
            ad.setCanRemove(removableReviewers.contains(id));
            byUser.put(id, ad);
          }
          if (ai.has_value()) {
            ad.votable(name);
            ad.value(name, ai.value());
            String fv = formatValue(ai.value());
            if (fv.equals(max)) {
              ad.approved(name);
            } else if (ai.value() < 0 && fv.equals(min)) {
              ad.rejected(name);
            }
          }
        }
      }
    }
    return missing;
  }

  private Set<Integer> removableReviewers() {
    Set<Integer> result =
        new HashSet<Integer>(info.removable_reviewers().length());
    for (int i = 0; i < info.removable_reviewers().length(); i++) {
      result.add(info.removable_reviewers().get(i)._account_id());
    }
    return result;
  }

  /**
   * Sets the header row
   *
   * @param labels The list of labels to display in the header. This list does
   *    not get resorted, so be sure that the list's elements are in the same
   *    order as the list of labels passed to the {@code displayRow} method.
   */
  private void displayHeader(Collection<String> labels) {
    table.resizeColumns(2 + labels.size());

    final CellFormatter fmt = table.getCellFormatter();
    int col = 0;

    table.setText(0, col, Util.C.approvalTableReviewer());
    fmt.setStyleName(0, col, R.css().header2());
    col++;

    table.clearCell(0, col);
    fmt.setStyleName(0, col, R.css().header2());
    col++;

    for (String name : labels) {
      table.setText(0, col, name);
      fmt.setStyleName(0, col, R.css().header2());
      col++;
    }
    fmt.addStyleName(0, col - 1, R.css().rightmost2());
  }

  /**
   * Sets the reviewer data for a row.
   *
   * @param row The number of the row on which to set the reviewer.
   * @param ad The details for this reviewer's approval.
   * @param labels The list of labels to show. This list does not get resorted,
   *    so be sure that the list's elements are in the same order as the list
   *    of labels passed to the {@code displayHeader} method.
   * @param account The account information for the approval.
   */
  private void displayRow(int row, final ApprovalDetail ad,
      List<String> labels, AccountInfo account) {
    final CellFormatter fmt = table.getCellFormatter();
    int col = 0;

    table.setWidget(row, col++, new AccountLinkPanel(account));

    if (ad.canRemove()) {
      final PushButton remove = new PushButton( //
          new Image(Util.R.removeReviewerNormal()), //
          new Image(Util.R.removeReviewerPressed()));
      remove.setTitle(Util.M.removeReviewer(account.name()));
      remove.setStyleName(R.css().removeReviewer2());
      remove.addStyleName(R.css().link2());
      remove.addClickHandler(new ClickHandler() {
        @Override
        public void onClick(ClickEvent event) {
          doRemove(ad, remove);
        }
      });
      table.setWidget(row, col, remove);
    } else {
      table.clearCell(row, col);
    }
    fmt.setStyleName(row, col++, R.css().removeReviewerCell2());

    for (String labelName : labels) {
      fmt.setStyleName(row, col, R.css().approvalscore2());
      if (!ad.canVote(labelName)) {
        fmt.addStyleName(row, col, R.css().notVotable2());
        fmt.getElement(row, col).setTitle(Gerrit.C.userCannotVoteToolTip());
      }

      if (ad.isRejected(labelName)) {
        table.setWidget(row, col, new Image(Gerrit.RESOURCES.redNot()));

      } else if (ad.isApproved(labelName)) {
        table.setWidget(row, col, new Image(Gerrit.RESOURCES.greenCheck()));

      } else {
        int v = ad.getValue(labelName);
        if (v == 0) {
          table.clearCell(row, col);
          col++;
          continue;
        }
        String vstr = String.valueOf(ad.getValue(labelName));
        if (v > 0) {
          vstr = "+" + vstr;
          fmt.addStyleName(row, col, R.css().posscore2());
        } else {
          fmt.addStyleName(row, col, R.css().negscore2());
        }
        table.setText(row, col, vstr);
      }

      col++;
    }

    fmt.addStyleName(row, col - 1, R.css().rightmost2());
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
    openForm.setVisible(true);
    UIObject.setVisible(form, false);
    suggestBox.setFocus(false);
  }

  private void addReviewer(final String reviewer, boolean confirmed) {
    ChangeApi.reviewers(info.legacy_id().get()).post(
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
    RestApi call = ChangeApi.detail(info.legacy_id().get());
    ChangeList.addOptions(call, EnumSet.of(
      ListChangesOption.CURRENT_REVISION));
    call.get(new GerritCallback<ChangeInfo>() {
      @Override
      public void onSuccess(ChangeInfo info) {
        Reviewers.this.info = info;
        display(info);
        renderRemoveReviewers();
      }
    });
  }

  private void display(ChangeInfo info) {
    renderReviewers(info);
    labels.clear();
    labels.set(info, revision);
  }

  private void renderReviewers(ChangeInfo info) {
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
    r.remove(info.owner()._account_id());
    cc.remove(info.owner()._account_id());
    reviewers.setInnerSafeHtml(Labels.formatUserList(style, r.values()));
    setCcReviewers(Labels.formatUserList(style, cc.values()));
  }

  private void doRemove(ApprovalDetail ad, final PushButton remove) {
    remove.setEnabled(false);
    ChangeApi.reviewer(info.legacy_id().get(), ad.getAccount().get())
      .delete(new GerritCallback<JavaScriptObject>() {
          @Override
          public void onSuccess(JavaScriptObject result) {
            updateReviewerList();
          }

          @Override
          public void onFailure(final Throwable caught) {
            remove.setEnabled(true);
            super.onFailure(caught);
          }
        });
  }
}
