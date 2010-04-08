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

import com.google.gerrit.client.ErrorDialog;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.patches.PatchUtil;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.AccountDashboardLink;
import com.google.gerrit.client.ui.AddMemberBox;
import com.google.gerrit.common.data.AccountInfoCache;
import com.google.gerrit.common.data.ApprovalDetail;
import com.google.gerrit.common.data.ApprovalType;
import com.google.gerrit.common.data.ChangeDetail;
import com.google.gerrit.common.data.ReviewerResult;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.ApprovalCategoryValue;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.PatchSetApproval;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Displays a table of {@link ApprovalDetail} objects for a change record. */
public class ApprovalTable extends Composite {
  private final List<ApprovalType> types;
  private final Grid table;
  private final Widget missing;
  private final Panel addReviewer;
  private final AddMemberBox addMemberBox;
  private Change.Id changeId;
  private boolean changeIsOpen;
  private AccountInfoCache accountCache = AccountInfoCache.empty();

  public ApprovalTable() {
    types = Gerrit.getConfig().getApprovalTypes().getApprovalTypes();
    table = new Grid(1, 4 + types.size());
    table.addStyleName(Gerrit.RESOURCES.css().infoTable());
    displayHeader();

    missing = new Widget() {
      {
        setElement(DOM.createElement("ul"));
      }
    };
    missing.setStyleName(Gerrit.RESOURCES.css().missingApprovalList());

    addReviewer = new FlowPanel();
    addReviewer.setStyleName(Gerrit.RESOURCES.css().addReviewer());
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

    setStyleName(Gerrit.RESOURCES.css().approvalTable());
  }

  private void displayHeader() {
    int col = 0;
    header(col++, Util.C.approvalTableReviewer());
    header(col++, "");

    for (final ApprovalType t : types) {
      header(col++, t.getCategory().getName());
    }
    header(col++, Util.C.removeReviewer());
    applyEdgeStyles(0);
  }

  private void header(final int col, final String title) {
    table.setText(0, col, title);
    table.getCellFormatter().addStyleName(0, col, Gerrit.RESOURCES.css().header());
  }

  private void applyEdgeStyles(final int row) {
    final CellFormatter fmt = table.getCellFormatter();
    fmt.addStyleName(row, 1, Gerrit.RESOURCES.css().approvalrole());
    fmt.addStyleName(row, 1 + types.size(), Gerrit.RESOURCES.css().rightmost());
    fmt.addStyleName(row, 2 + types.size(), Gerrit.RESOURCES.css().rightmost());
    fmt.addStyleName(row, 3 + types.size(), Gerrit.RESOURCES.css().approvalhint());
  }

  private void applyScoreStyles(final int row) {
    final CellFormatter fmt = table.getCellFormatter();
    for (int col = 0; col < types.size(); col++) {
      fmt.addStyleName(row, 2 + col, Gerrit.RESOURCES.css().approvalscore());
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

    final Element missingList = missing.getElement();
    while (DOM.getChildCount(missingList) > 0) {
      DOM.removeChild(missingList, DOM.getChild(missingList, 0));
    }

    missing.setVisible(false);
    if (need != null) {
      for (final ApprovalType at : types) {
        if (need.contains(at.getCategory().getId())) {
          final Element li = DOM.createElement("li");
          li.setClassName(Gerrit.RESOURCES.css().missingApproval());
          DOM.setInnerText(li, Util.M.needApproval(at.getCategory().getName(),
              at.getMax().formatValue(), at.getMax().getName()));
          DOM.appendChild(missingList, li);
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
        new GerritCallback<ReviewerResult>() {
          public void onSuccess(final ReviewerResult result) {
            addMemberBox.setEnabled(true);
            addMemberBox.setText("");

            if (!result.getErrors().isEmpty()) {
              final SafeHtmlBuilder r = new SafeHtmlBuilder();
              for (final ReviewerResult.Error e : result.getErrors()) {
                switch (e.getType()) {
                  case ACCOUNT_NOT_FOUND:
                    r.append(Util.M.accountNotFound(e.getName()));
                    break;

                  case CHANGE_NOT_VISIBLE:
                    r.append(Util.M.changeNotVisibleTo(e.getName()));
                    break;

                  default:
                    r.append(e.getName());
                    r.append(" - ");
                    r.append(e.getType());
                    r.br();
                    break;
                }
              }
              new ErrorDialog(r).center();
            }

            final ChangeDetail r = result.getChange();
            if (r != null) {
              setAccountInfoCache(r.getAccounts());
              display(r.getChange(), r.getMissingApprovals(), r.getApprovals());
            }
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
    final Map<ApprovalCategory.Id, PatchSetApproval> am = ad.getApprovalMap();
    final StringBuilder hint = new StringBuilder();
    int col = 0;
    table.setWidget(row, col++, link(ad.getAccount()));
    table.clearCell(row, col++); // TODO populate the account role

    for (final ApprovalType type : types) {
      final PatchSetApproval ca = am.get(type.getCategory().getId());
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
        table.setWidget(row, col, new Image(Gerrit.RESOURCES.redNot()));

      } else if (type.isMaxPositive(ca)) {
        table.setWidget(row, col, new Image(Gerrit.RESOURCES.greenCheck()));

      } else {
        String vstr = String.valueOf(ca.getValue());
        if (ca.getValue() > 0) {
          vstr = "+" + vstr;
          fmt.removeStyleName(row, col, Gerrit.RESOURCES.css().negscore());
          fmt.addStyleName(row, col, Gerrit.RESOURCES.css().posscore());
        } else {
          fmt.addStyleName(row, col, Gerrit.RESOURCES.css().negscore());
          fmt.removeStyleName(row, col, Gerrit.RESOURCES.css().posscore());
        }
        table.setText(row, col, vstr);
      }

      col++;
    }

    //
    // Remove button
    //
    Button removeButton = new Button("X");
    removeButton.setStyleName(Gerrit.RESOURCES.css().removeReviewer());
    removeButton.addClickHandler(new ClickHandler() {

      @Override
      public void onClick(ClickEvent event) {
        PatchUtil.DETAIL_SVC.removeReviewer(changeId, ad.getAccount(),
            new GerritCallback<ReviewerResult>() {

              @Override
              public void onSuccess(ReviewerResult result) {
                final ChangeDetail r = result.getChange();
                display(r.getChange(), r.getMissingApprovals(), r.getApprovals());
              }

        });
      }

    });
    table.setWidget(row, col++, removeButton);
    table.setText(row, col++, hint.toString());
  }
}
