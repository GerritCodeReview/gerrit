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

import com.google.gerrit.client.ConfirmationCallback;
import com.google.gerrit.client.ConfirmationDialog;
import com.google.gerrit.client.ErrorDialog;
import com.google.gerrit.client.FormatUtil;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.patches.PatchUtil;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.ReviewerSuggestOracle;
import com.google.gerrit.client.ui.AccountDashboardLink;
import com.google.gerrit.client.ui.AddMemberBox;
import com.google.gerrit.common.data.AccountInfoCache;
import com.google.gerrit.common.data.ApprovalDetail;
import com.google.gerrit.common.data.ApprovalType;
import com.google.gerrit.common.data.ApprovalTypes;
import com.google.gerrit.common.data.ChangeDetail;
import com.google.gerrit.common.data.ReviewerResult;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.PatchSetApproval;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.PushButton;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/** Displays a table of {@link ApprovalDetail} objects for a change record. */
public class ApprovalTable extends Composite {
  private final ApprovalTypes types;
  private final Grid table;
  private final Widget missing;
  private final Panel addReviewer;
  private final ReviewerSuggestOracle reviewerSuggestOracle;
  private final AddMemberBox addMemberBox;
  private Change.Id changeId;
  private AccountInfoCache accountCache = AccountInfoCache.empty();

  public ApprovalTable() {
    types = Gerrit.getConfig().getApprovalTypes();
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

  private void displayHeader(List<String> labels) {
    table.resizeColumns(2 + labels.size());

    final CellFormatter fmt = table.getCellFormatter();
    int col = 0;

    table.setText(0, col, Util.C.approvalTableReviewer());
    fmt.setStyleName(0, col, Gerrit.RESOURCES.css().header());
    col++;

    table.clearCell(0, col);
    fmt.setStyleName(0, col, Gerrit.RESOURCES.css().header());
    col++;

    for (String name : labels) {
      table.setText(0, col, name);
      fmt.setStyleName(0, col, Gerrit.RESOURCES.css().header());
      col++;
    }
    fmt.addStyleName(0, col - 1, Gerrit.RESOURCES.css().rightmost());
  }

  public void setAccountInfoCache(final AccountInfoCache aic) {
    assert aic != null;
    accountCache = aic;
  }

  private AccountDashboardLink link(final Account.Id id) {
    return AccountDashboardLink.link(accountCache, id);
  }

  void display(ChangeDetail detail) {
    reviewerSuggestOracle.setProject(detail.getChange().getProject());

    List<String> columns = new ArrayList<String>();
    List<ApprovalDetail> rows = detail.getApprovals();

    changeId = detail.getChange().getId();

    final Element missingList = missing.getElement();
    while (DOM.getChildCount(missingList) > 0) {
      DOM.removeChild(missingList, DOM.getChild(missingList, 0));
    }
    missing.setVisible(false);

    if (detail.getSubmitRecords() != null) {
      HashSet<String> reportedMissing = new HashSet<String>();

      HashMap<Account.Id, ApprovalDetail> byUser =
          new HashMap<Account.Id, ApprovalDetail>();
      for (ApprovalDetail ad : detail.getApprovals()) {
        byUser.put(ad.getAccount(), ad);
      }

      for (SubmitRecord rec : detail.getSubmitRecords()) {
        if (rec.labels == null) {
          continue;
        }

        for (SubmitRecord.Label lbl : rec.labels) {
          if (!columns.contains(lbl.label)) {
            columns.add(lbl.label);
          }

          switch (lbl.status) {
            case OK: {
              ApprovalDetail ad = byUser.get(lbl.appliedBy);
              if (ad != null) {
                ad.approved(lbl.label);
              }
              break;
            }

            case REJECT: {
              ApprovalDetail ad = byUser.get(lbl.appliedBy);
              if (ad != null) {
                ad.rejected(lbl.label);
              }
              break;
            }

            case NEED:
            case IMPOSSIBLE:
              if (reportedMissing.add(lbl.label)) {
                Element li = DOM.createElement("li");
                li.setClassName(Gerrit.RESOURCES.css().missingApproval());
                DOM.setInnerText(li, Util.M.needApproval(lbl.label));
                DOM.appendChild(missingList, li);
              }
              break;
          }
        }
      }
      missing.setVisible(!reportedMissing.isEmpty());

    } else {
      for (ApprovalDetail ad : rows) {
        for (PatchSetApproval psa : ad.getPatchSetApprovals()) {
          ApprovalType legacyType = types.byId(psa.getCategoryId());
          if (legacyType == null) {
            continue;
          }
          String labelName = legacyType.getCategory().getLabelName();
          if (psa.getValue() == legacyType.getMax().getValue()) {
            ad.approved(labelName);
          } else if (psa.getValue() == legacyType.getMin().getValue()) {
            ad.rejected(labelName);
          }
          if (!columns.contains(labelName)) {
            columns.add(labelName);
          }
        }
        Collections.sort(columns, new Comparator<String>() {
          @Override
          public int compare(String o1, String o2) {
            ApprovalType a = types.byLabel(o1);
            ApprovalType b = types.byLabel(o2);
            int cmp = 0;
            if (a != null && b != null) {
              cmp = a.getCategory().getPosition() - b.getCategory().getPosition();
            }
            if (cmp == 0) {
              cmp = o1.compareTo(o2);
            }
            return cmp;
          }
        });
      }
    }

    if (rows.isEmpty()) {
      table.setVisible(false);
    } else {
      displayHeader(columns);
      table.resizeRows(1 + rows.size());
      for (int i = 0; i < rows.size(); i++) {
        displayRow(i + 1, rows.get(i), detail.getChange(), columns);
      }
      table.setVisible(true);
    }

    addReviewer.setVisible(Gerrit.isSignedIn());
  }

  private void doAddReviewer() {
    final String reviewer = addMemberBox.getText();
    if (reviewer.length() == 0) {
      return;
    }

    addMemberBox.setEnabled(false);
    final List<String> reviewers = new ArrayList<String>();
    reviewers.add(reviewer);

    addReviewers(reviewers, false);
  }

  private void addReviewers(final List<String> reviewers,
      final boolean confirmed) {
    PatchUtil.DETAIL_SVC.addReviewers(changeId, reviewers, confirmed,
        new GerritCallback<ReviewerResult>() {
          public void onSuccess(final ReviewerResult result) {
            addMemberBox.setEnabled(true);
            addMemberBox.setText("");

            final ChangeDetail changeDetail = result.getChange();
            if (changeDetail != null) {
              setAccountInfoCache(changeDetail.getAccounts());
              display(changeDetail);
            }

            if (!result.getErrors().isEmpty()) {
              final SafeHtmlBuilder r = new SafeHtmlBuilder();
              for (final ReviewerResult.Error e : result.getErrors()) {
                switch (e.getType()) {
                  case REVIEWER_NOT_FOUND:
                    r.append(Util.M.reviewerNotFound(e.getName()));
                    break;

                  case ACCOUNT_INACTIVE:
                    r.append(Util.M.accountInactive(e.getName()));
                    break;

                  case CHANGE_NOT_VISIBLE:
                    r.append(Util.M.changeNotVisibleTo(e.getName()));
                    break;

                  case GROUP_EMPTY:
                    r.append(Util.M.groupIsEmpty(e.getName()));
                    break;

                  case GROUP_HAS_TOO_MANY_MEMBERS:
                    if (result.askForConfirmation() && !confirmed) {
                      askForConfirmation(e.getName(), result.getMemberCount());
                      return;
                    } else {
                      r.append(Util.M.groupHasTooManyMembers(e.getName()));
                    }
                    break;

                  case GROUP_NOT_ALLOWED:
                    r.append(Util.M.groupIsNotAllowed(e.getName()));
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
          }

          private void askForConfirmation(final String groupName,
              final int memberCount) {
            final StringBuilder message = new StringBuilder();
            message.append("<b>");
            message.append(Util.M.groupManyMembersConfirmation(groupName,
                memberCount));
            message.append("</b>");
            final ConfirmationDialog confirmationDialog =
                new ConfirmationDialog(Util.C
                    .approvalTableAddManyReviewersConfirmationDialogTitle(),
                    new HTML(message.toString()), new ConfirmationCallback() {
                      @Override
                      public void onOk() {
                        addReviewers(reviewers, true);
                      }
                    });
            confirmationDialog.center();
          }

          @Override
          public void onFailure(final Throwable caught) {
            addMemberBox.setEnabled(true);
            super.onFailure(caught);
          }
        });
  }

  private void displayRow(final int row, final ApprovalDetail ad,
      final Change change, List<String> columns) {
    final CellFormatter fmt = table.getCellFormatter();
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

    for (String labelName : columns) {
      fmt.setStyleName(row, col, Gerrit.RESOURCES.css().approvalscore());

      if (ad.isRejected(labelName)) {
        table.setWidget(row, col, new Image(Gerrit.RESOURCES.redNot()));

      } else if (ad.isApproved(labelName)) {
        table.setWidget(row, col, new Image(Gerrit.RESOURCES.greenCheck()));

      } else {
        ApprovalType legacyType = types.byLabel(labelName);
        if (legacyType == null) {
          table.clearCell(row, col);
          col++;
          continue;
        }

        PatchSetApproval ca = ad.getPatchSetApproval(legacyType.getCategory().getId());
        if (ca == null || ca.getValue() == 0) {
          table.clearCell(row, col);
          col++;
          continue;
        }

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

    fmt.addStyleName(row, col - 1, Gerrit.RESOURCES.css().rightmost());
  }

  private void doRemove(final ApprovalDetail ad, final PushButton remove) {
    remove.setEnabled(false);
    PatchUtil.DETAIL_SVC.removeReviewer(changeId, ad.getAccount(),
        new GerritCallback<ReviewerResult>() {
          @Override
          public void onSuccess(ReviewerResult result) {
            if (result.getErrors().isEmpty()) {
              final ChangeDetail r = result.getChange();
              display(r);
            } else {
              final ReviewerResult.Error resultError =
                  result.getErrors().get(0);
              String message;
              switch (resultError.getType()) {
                case REMOVE_NOT_PERMITTED:
                  message = Util.C.approvalTableRemoveNotPermitted();
                  break;
                case COULD_NOT_REMOVE:
                default:
                  message = Util.C.approvalTableCouldNotRemove();
              }
              new ErrorDialog(message + " " + resultError.getName()).center();
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
