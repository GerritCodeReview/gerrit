// Copyright 2008 Google Inc.
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
import com.google.gerrit.client.data.AccountInfoCache;
import com.google.gerrit.client.data.ApprovalDetail;
import com.google.gerrit.client.data.ApprovalType;
import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.ApprovalCategory;
import com.google.gerrit.client.reviewdb.ApprovalCategoryValue;
import com.google.gerrit.client.reviewdb.ChangeApproval;
import com.google.gerrit.client.ui.AccountDashboardLink;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;

import java.util.List;
import java.util.Map;

/** Displays a table of {@link ApprovalDetail} objects for a change record. */
public class ApprovalTable extends Composite {
  private final List<ApprovalType> types;
  private final Grid table;
  private AccountInfoCache accountCache = AccountInfoCache.empty();

  public ApprovalTable() {
    types = Gerrit.getGerritConfig().getApprovalTypes();
    table = new Grid(1, 3 + types.size());
    table.addStyleName("gerrit-InfoTable");
    displayHeader();

    initWidget(table);
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

  public void display(final List<ApprovalDetail> rows) {
    final int oldcnt = table.getRowCount();
    table.resizeRows(1 + rows.size());
    if (oldcnt < 1 + rows.size()) {
      for (int row = oldcnt; row < 1 + rows.size(); row++) {
        applyEdgeStyles(row);
        applyScoreStyles(row);
      }
    }

    for (int i = 0; i < rows.size(); i++) {
      displayRow(i + 1, rows.get(i));
    }
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
