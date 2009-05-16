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

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.SignOutHandler;
import com.google.gerrit.client.data.AccountInfoCache;
import com.google.gerrit.client.data.ApprovalDetail;
import com.google.gerrit.client.data.ApprovalType;
import com.google.gerrit.client.data.ChangeDetail;
import com.google.gerrit.client.patches.PatchUtil;
import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.ApprovalCategory;
import com.google.gerrit.client.reviewdb.ApprovalCategoryValue;
import com.google.gerrit.client.reviewdb.Change;
import com.google.gerrit.client.reviewdb.ChangeApproval;
import com.google.gerrit.client.rpc.Common;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.AccountDashboardLink;
import com.google.gerrit.client.ui.AddMemberBox;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;
import com.google.gwtjsonrpc.client.VoidResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Displays a table of {@link ApprovalDetail} objects for a change record. */
public class ApprovalTable extends Composite {
  private final List<ApprovalType> types;
  private final Grid table;
  private final Panel missing;
  private final Panel addReviewer;
  private final AddMemberBox addMemberBox;
  private final SignOutHandler signedInListener;
  private Change.Id changeId;
  private boolean changeIsOpen;
  private AccountInfoCache accountCache = AccountInfoCache.empty();

  public ApprovalTable() {
    types = Common.getGerritConfig().getApprovalTypes();
    table = new Grid(1, 3 + types.size());
    table.addStyleName("gerrit-InfoTable");
    displayHeader();

    missing = new FlowPanel();
    missing.setStyleName("gerrit-Change-MissingApprovalList");

    addReviewer = new FlowPanel();
    addReviewer.setStyleName("gerrit-Change-AddReviewer");
    addMemberBox = new AddMemberBox();
    addMemberBox.setAddButtonText(Util.C.approvalTableAddReviewer());
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

    signedInListener = new SignOutHandler() {
      public void onSignOut() {
        addReviewer.setVisible(false);
      }
    };
  }

  @Override
  protected void onLoad() {
    Gerrit.addSignOutHandler(signedInListener);
    super.onLoad();
  }

  @Override
  protected void onUnload() {
    Gerrit.removeSignOutHandler(signedInListener);
    super.onUnload();
  }

  private void displayHeader() {
    int col = 0;
    header(col++, Util.C.approvalTableReviewer());
    header(col++, "");

    for (final ApprovalType t : types) {
      header(col++, t.getCategory().getName());
    }
    applyEdgeStyles(0);
  }

  private void header(final int col, final String title) {
    table.setText(0, col, title);
    table.getCellFormatter().addStyleName(0, col, "header");
  }

  private void applyEdgeStyles(final int row) {
    final CellFormatter fmt = table.getCellFormatter();
    fmt.addStyleName(row, 0, "leftmost");
    fmt.addStyleName(row, 0, "reviewer");
    fmt.addStyleName(row, 1, "approvalrole");
    fmt.addStyleName(row, 1 + types.size(), "rightmost");
    fmt.addStyleName(row, 2 + types.size(), "approvalhint");
  }

  private void applyScoreStyles(final int row) {
    final CellFormatter fmt = table.getCellFormatter();
    for (int col = 0; col < types.size(); col++) {
      fmt.addStyleName(row, 2 + col, "approvalscore");
    }
  }

  public void setAccountInfoCache(final AccountInfoCache aic) {
    assert aic != null;
    accountCache = aic;
  }

  private AccountDashboardLink link(final Account.Id id) {
    return AccountDashboardLink.link(accountCache, id);
  }

  public void display(final Change change, final Set<ApprovalCategory.Id> need,
      final List<ApprovalDetail> rows) {
    changeId = change.getId();

    final int oldcnt = table.getRowCount();
    table.resizeRows(1 + rows.size());
    if (oldcnt < 1 + rows.size()) {
      for (int row = oldcnt; row < 1 + rows.size(); row++) {
        applyEdgeStyles(row);
        applyScoreStyles(row);
      }
    }

    if (rows.isEmpty()) {
      table.setVisible(false);
    } else {
      table.setVisible(true);
      for (int i = 0; i < rows.size(); i++) {
        displayRow(i + 1, rows.get(i));
      }
    }

    missing.clear();
    missing.setVisible(false);
    if (need != null) {
      for (final ApprovalType at : types) {
        if (need.contains(at.getCategory().getId())) {
          final Label l =
              new Label(Util.M.needApproval(at.getCategory().getName()));
          l.setStyleName("gerrit-Change-MissingApproval");
          missing.add(l);
          missing.setVisible(true);
        }
      }
    }

    changeIsOpen = change.getStatus().isOpen();
    addReviewer.setVisible(Gerrit.isSignedIn() && changeIsOpen);
  }

  private void doAddReviewer() {
    final String nameEmail = addMemberBox.getText();
    if (nameEmail.length() == 0) {
      return;
    }

    addMemberBox.setEnabled(false);
    final List<String> reviewers = new ArrayList<String>();
    reviewers.add(nameEmail);

    PatchUtil.DETAIL_SVC.addReviewers(changeId, reviewers,
        new GerritCallback<VoidResult>() {
          public void onSuccess(final VoidResult result) {
            addMemberBox.setEnabled(true);
            addMemberBox.setText("");
            Util.DETAIL_SVC.changeDetail(changeId,
                new GerritCallback<ChangeDetail>() {
                  public void onSuccess(final ChangeDetail r) {
                    if (isAttached()) {
                      setAccountInfoCache(r.getAccounts());
                      display(r.getChange(), r.getMissingApprovals(), r
                          .getApprovals());
                    }
                  }
                });
          }

          @Override
          public void onFailure(final Throwable caught) {
            addMemberBox.setEnabled(true);
            super.onFailure(caught);
          }
        });
  }

  private void displayRow(final int row, final ApprovalDetail ad) {
    final CellFormatter fmt = table.getCellFormatter();
    final Map<ApprovalCategory.Id, ChangeApproval> am = ad.getApprovalMap();
    final StringBuilder hint = new StringBuilder();
    int col = 0;
    table.setWidget(row, col++, link(ad.getAccount()));
    table.clearCell(row, col++); // TODO populate the account role

    for (final ApprovalType type : types) {
      final ChangeApproval ca = am.get(type.getCategory().getId());
      if (ca == null || ca.getValue() == 0) {
        table.clearCell(row, col);
        col++;
        continue;
      }

      final ApprovalCategoryValue acv = type.getValue(ca);
      if (acv != null) {
        if (hint.length() > 0) {
          hint.append("; ");
        }
        hint.append(acv.getName());
      }

      if (type.isMaxNegative(ca)) {
        table.setWidget(row, col, Gerrit.ICONS.redNot().createImage());

      } else if (type.isMaxPositive(ca)) {
        table.setWidget(row, col, Gerrit.ICONS.greenCheck().createImage());

      } else {
        String vstr = String.valueOf(ca.getValue());
        if (ca.getValue() > 0) {
          vstr = "+" + vstr;
          fmt.removeStyleName(row, col, "negscore");
          fmt.addStyleName(row, col, "posscore");
        } else {
          fmt.addStyleName(row, col, "negscore");
          fmt.removeStyleName(row, col, "posscore");
        }
        table.setText(row, col, vstr);
      }

      col++;
    }

    table.setText(row, col++, hint.toString());
  }
}
