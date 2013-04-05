// Copyright (C) 2008 The Android Open Source Project
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

import static com.google.gerrit.common.data.LabelValue.formatValue;

import com.google.gerrit.client.ConfirmationCallback;
import com.google.gerrit.client.ConfirmationDialog;
import com.google.gerrit.client.ErrorDialog;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.account.AccountInfo;
import com.google.gerrit.client.changes.ChangeInfo.ApprovalInfo;
import com.google.gerrit.client.changes.ChangeInfo.LabelInfo;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.NativeMap;
import com.google.gerrit.client.rpc.NativeString;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.client.ui.AccountLink;
import com.google.gerrit.client.ui.AddMemberBox;
import com.google.gerrit.client.ui.ReviewerSuggestOracle;
import com.google.gerrit.common.data.ApprovalDetail;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.PushButton;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Displays a table of {@link ApprovalDetail} objects for a change record. */
public class ApprovalTable extends Composite {
  private final Grid table;
  private final Widget missing;
  private final Panel addReviewer;
  private final ReviewerSuggestOracle reviewerSuggestOracle;
  private final AddMemberBox addMemberBox;
  private ChangeInfo lastChange;
  private Map<Integer, Integer> rows;

  public ApprovalTable() {
    rows = new HashMap<Integer, Integer>();
    table = new Grid(1, 3);
    table.addStyleName(Gerrit.RESOURCES.css().infoTable());

    missing = new Widget() {
      {
        setElement(DOM.createElement("ul"));
      }
    };
    missing.setStyleName(Gerrit.RESOURCES.css().missingApprovalList());

    addReviewer = new FlowPanel();
    addReviewer.setStyleName(Gerrit.RESOURCES.css().addReviewer());
    reviewerSuggestOracle = new ReviewerSuggestOracle();
    addMemberBox =
        new AddMemberBox(Util.C.approvalTableAddReviewer(),
            Util.C.approvalTableAddReviewerHint(), reviewerSuggestOracle);
    addMemberBox.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        doAddReviewer();
      }
    });
    addReviewer.add(addMemberBox);
    addReviewer.setVisible(false);

    final FlowPanel fp = new FlowPanel();
    fp.add(table);
    fp.add(missing);
    fp.add(addReviewer);
    initWidget(fp);

    setStyleName(Gerrit.RESOURCES.css().approvalTable());
  }

  private void displayHeader(Collection<String> labels) {
    table.resizeColumns(2 + labels.size());

    final CellFormatter fmt = table.getCellFormatter();
    int col = 0;

    table.setText(0, col, Util.C.approvalTableReviewer());
    fmt.setStyleName(0, col, Gerrit.RESOURCES.css().header());
    col++;

    table.clearCell(0, col);
    fmt.setStyleName(0, col, Gerrit.RESOURCES.css().header());
    col++;

    List<String> sorted_labels = new ArrayList<String>();
    sorted_labels.addAll(labels);
    Collections.sort(sorted_labels);

    for (String name : sorted_labels) {
      table.setText(0, col, name);
      fmt.setStyleName(0, col, Gerrit.RESOURCES.css().header());
      col++;
    }
    fmt.addStyleName(0, col - 1, Gerrit.RESOURCES.css().rightmost());
  }

  void display(ChangeInfo change) {
    lastChange = change;
    reviewerSuggestOracle.setChange(change.legacy_id());
    Map<Integer, ApprovalDetail> byUser =
        new LinkedHashMap<Integer, ApprovalDetail>();
    Map<Integer, AccountInfo> accounts =
        new LinkedHashMap<Integer, AccountInfo>();
    List<String> missingLabels = initLabels(change, accounts, byUser);

    removeAllChildren(missing.getElement());
    for (String label : missingLabels) {
      addMissingLabel(Util.M.needApproval(label));
    }

    if (byUser.isEmpty()) {
      table.setVisible(false);
    } else {
      displayHeader(change.labels());
      table.resizeRows(1 + byUser.size());
      int i = 1;
      for (ApprovalDetail ad : ApprovalDetail.sort(
          byUser.values(), change.owner()._account_id())) {
        displayRow(i++, ad, change, accounts.get(ad.getAccount().get()));
      }
      table.setVisible(true);
    }

    if (Gerrit.getConfig().testChangeMerge()
        && change.status() != Change.Status.MERGED
        && !change.mergeable()) {
      addMissingLabel(Util.C.messageNeedsRebaseOrHasDependency());
    }
    missing.setVisible(DOM.getChildCount(missing.getElement()) > 0);
    addReviewer.setVisible(Gerrit.isSignedIn());
  }

  private void removeAllChildren(Element el) {
    for (int i = DOM.getChildCount(el) - 1; i >= 0; i--) {
      DOM.removeChild(el, DOM.getChild(el, i));
    }
  }

  private void addMissingLabel(String text) {
    Element li = DOM.createElement("li");
    li.setClassName(Gerrit.RESOURCES.css().missingApproval());
    DOM.setInnerText(li, text);
    DOM.appendChild(missing.getElement(), li);
  }

  private Set<Integer> removableReviewers(ChangeInfo change) {
    Set<Integer> result =
        new HashSet<Integer>(change.removable_reviewers().length());
    for (int i = 0; i < change.removable_reviewers().length(); i++) {
      result.add(change.removable_reviewers().get(i)._account_id());
    }
    return result;
  }

  private List<String> initLabels(ChangeInfo change,
      Map<Integer, AccountInfo> accounts,
      Map<Integer, ApprovalDetail> byUser) {
    Set<Integer> removableReviewers = removableReviewers(change);
    List<String> missing = new ArrayList<String>();
    for (String name : change.labels()) {
      LabelInfo label = change.label(name);

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

  private void doAddReviewer() {
    String reviewer = addMemberBox.getText();
    if (!reviewer.isEmpty()) {
      addMemberBox.setEnabled(false);
      addReviewer(reviewer, false);
    }
  }

  private static class PostInput extends JavaScriptObject {
    static PostInput create(String reviewer, boolean confirmed) {
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

  private static class ReviewerInfo extends AccountInfo {
    final Set<String> approvals() {
      return Natives.keys(_approvals());
    }
    final native String approval(String l) /*-{ return this.approvals[l]; }-*/;
    private final native NativeMap<NativeString> _approvals() /*-{ return this.approvals; }-*/;

    protected ReviewerInfo() {
    }
  }

  private static class PostResult extends JavaScriptObject {
    final native JsArray<ReviewerInfo> reviewers() /*-{ return this.reviewers; }-*/;
    final native boolean confirm() /*-{ return this.confirm || false; }-*/;
    final native String error() /*-{ return this.error; }-*/;

    protected PostResult() {
    }
  }

  private void addReviewer(final String reviewer, boolean confirmed) {
    ChangeApi.reviewers(lastChange.legacy_id().get()).post(
        PostInput.create(reviewer, confirmed),
        new GerritCallback<PostResult>() {
          public void onSuccess(PostResult result) {
            addMemberBox.setEnabled(true);
            addMemberBox.setText("");
            if (result.error() == null) {
              reload();
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
            addMemberBox.setEnabled(true);
            if (isNoSuchEntity(caught)) {
              new ErrorDialog(Util.M.reviewerNotFound(reviewer)).center();
            } else {
              super.onFailure(caught);
            }
          }
        });
  }

  private void displayRow(int row, final ApprovalDetail ad, ChangeInfo change,
      AccountInfo account) {
    final CellFormatter fmt = table.getCellFormatter();
    int col = 0;

    table.setWidget(row, col++, new AccountLink(account));
    rows.put(account._account_id(), row);

    if (ad.canRemove()) {
      final PushButton remove = new PushButton( //
          new Image(Util.R.removeReviewerNormal()), //
          new Image(Util.R.removeReviewerPressed()));
      remove.setTitle(Util.M.removeReviewer(account.name()));
      remove.setStyleName(Gerrit.RESOURCES.css().removeReviewer());
      remove.addStyleName(Gerrit.RESOURCES.css().link());
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
    fmt.setStyleName(row, col++, Gerrit.RESOURCES.css().removeReviewerCell());

    for (String labelName : change.labels()) {
      fmt.setStyleName(row, col, Gerrit.RESOURCES.css().approvalscore());
      if (!ad.canVote(labelName)) {
        fmt.addStyleName(row, col, Gerrit.RESOURCES.css().notVotable());
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
          fmt.addStyleName(row, col, Gerrit.RESOURCES.css().posscore());
        } else {
          fmt.addStyleName(row, col, Gerrit.RESOURCES.css().negscore());
        }
        table.setText(row, col, vstr);
      }

      col++;
    }

    fmt.addStyleName(row, col - 1, Gerrit.RESOURCES.css().rightmost());
  }

  private void reload() {
    ChangeApi.detail(lastChange.legacy_id().get(),
        new GerritCallback<ChangeInfo>() {
          @Override
          public void onSuccess(ChangeInfo result) {
            display(result);
          }
        });
  }

  private void doRemove(ApprovalDetail ad, final PushButton remove) {
    remove.setEnabled(false);
    ChangeApi.reviewer(lastChange.legacy_id().get(), ad.getAccount().get())
      .delete(new GerritCallback<JavaScriptObject>() {
          @Override
          public void onSuccess(JavaScriptObject result) {
            reload();
          }

          @Override
          public void onFailure(final Throwable caught) {
            remove.setEnabled(true);
            super.onFailure(caught);
          }
        });
  }
}
