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
import com.google.gerrit.client.FormatUtil;
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
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.PushButton;
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
  private AccountInfoCache accountCache = AccountInfoCache.empty();

  public ApprovalTable() {
    types = Gerrit.getConfig().getApprovalTypes().getApprovalTypes();
    table = new Grid(1, 3 + types.size());
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
    final CellFormatter fmt = table.getCellFormatter();
    int col = 0;

    table.setText(0, col, Util.C.approvalTableReviewer());
    fmt.setStyleName(0, col, Gerrit.RESOURCES.css().header());
    col++;

    table.clearCell(0, col);
    fmt.setStyleName(0, col, Gerrit.RESOURCES.css().header());
    col++;

    for (final ApprovalType t : types) {
      table.setText(0, col, t.getCategory().getName());
      fmt.setStyleName(0, col, Gerrit.RESOURCES.css().header());
      col++;
    }

    table.clearCell(0, col);
    fmt.setStyleName(0, col, Gerrit.RESOURCES.css().header());
    fmt.addStyleName(0, col, Gerrit.RESOURCES.css().rightmost());
    col++;
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

    if (rows.isEmpty()) {
      table.setVisible(false);
    } else {
      table.resizeRows(1 + rows.size());
      for (int i = 0; i < rows.size(); i++) {
        displayRow(i + 1, rows.get(i), change);
      }
      table.setVisible(true);
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

    addReviewer.setVisible(Gerrit.isSignedIn() && change.getStatus().isOpen());
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

                  case ACCOUNT_INACTIVE:
                    r.append(Util.M.accountInactive(e.getName()));
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

  private void displayRow(final int row, final ApprovalDetail ad,
      final Change change) {
    final CellFormatter fmt = table.getCellFormatter();
    final Map<ApprovalCategory.Id, PatchSetApproval> am = ad.getApprovalMap();
    final StringBuilder hint = new StringBuilder();
    int col = 0;

    table.setWidget(row, col++, link(ad.getAccount()));

    if (ad.canRemove()) {
      final PushButton remove = new PushButton( //
          new Image(Util.R.removeReviewerNormal()), //
          new Image(Util.R.removeReviewerPressed()));
      remove.setTitle(Util.M.removeReviewer( //
          FormatUtil.name(accountCache.get(ad.getAccount()))));
      remove.setStyleName(Gerrit.RESOURCES.css().removeReviewer());
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

    for (final ApprovalType type : types) {
      fmt.setStyleName(row, col, Gerrit.RESOURCES.css().approvalscore());

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
          fmt.addStyleName(row, col, Gerrit.RESOURCES.css().posscore());
        } else {
          fmt.addStyleName(row, col, Gerrit.RESOURCES.css().negscore());
        }
        table.setText(row, col, vstr);
      }

      col++;
    }

    table.setText(row, col, hint.toString());
    fmt.setStyleName(row, col, Gerrit.RESOURCES.css().rightmost());
    fmt.addStyleName(row, col, Gerrit.RESOURCES.css().approvalhint());
    col++;
  }

  private void doRemove(final ApprovalDetail ad, final PushButton remove) {
    remove.setEnabled(false);
    PatchUtil.DETAIL_SVC.removeReviewer(changeId, ad.getAccount(),
        new GerritCallback<ReviewerResult>() {
          @Override
          public void onSuccess(ReviewerResult result) {
            if (result.getErrors().isEmpty()) {
              final ChangeDetail r = result.getChange();
              display(r.getChange(), r.getMissingApprovals(), r.getApprovals());
            } else {
              new ErrorDialog(result.getErrors().get(0).toString()).center();
            }
          }

          @Override
          public void onFailure(final Throwable caught) {
            remove.setEnabled(true);
            super.onFailure(caught);
          }
        });
  }
}
