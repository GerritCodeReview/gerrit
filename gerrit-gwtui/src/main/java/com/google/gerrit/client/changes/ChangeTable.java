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

import static com.google.gerrit.client.FormatUtil.shortFormat;

import com.google.gerrit.client.FormatUtil;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.patches.PatchUtil;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.AccountDashboardLink;
import com.google.gerrit.client.ui.ChangeLink;
import com.google.gerrit.client.ui.BranchTopicLink;
import com.google.gerrit.client.ui.NavigationTable;
import com.google.gerrit.client.ui.NeedsSignInKeyCommand;
import com.google.gerrit.client.ui.ProjectLink;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.common.data.AccountInfo;
import com.google.gerrit.common.data.AccountInfoCache;
import com.google.gerrit.common.data.ApprovalSummary;
import com.google.gerrit.common.data.ApprovalSummarySet;
import com.google.gerrit.common.data.ApprovalType;
import com.google.gerrit.common.data.ChangeInfo;
import com.google.gerrit.common.data.ToggleStarRequest;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountGeneralPreferences;
import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.ApprovalCategoryValue;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.PatchSetApproval;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.resources.client.ImageResource;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.UIObject;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;
import com.google.gwt.user.client.ui.HTMLTable.Cell;
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;
import com.google.gwtjsonrpc.client.VoidResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ChangeTable extends NavigationTable<ChangeInfo> {
  private static final int C_STAR = 1;
  private static final int C_ID = 2;
  private static final int C_SUBJECT = 3;
  private static final int C_OWNER = 4;
  private static final int C_PROJECT = 5;
  private static final int C_BRANCH = 6;
  private static final int C_LAST_UPDATE = 7;
  private static final int BASE_COLUMNS = 8;

  private final List<Section> sections;
  private AccountInfoCache accountCache = AccountInfoCache.empty();
  private final List<ApprovalType> approvalTypes;
  private final int columns;

  public ChangeTable() {
    this(false);
  }

  public ChangeTable(boolean showApprovals) {
    approvalTypes = Gerrit.getConfig().getApprovalTypes().getApprovalTypes();
    if (showApprovals) {
      columns = BASE_COLUMNS + approvalTypes.size();
    } else {
      columns = BASE_COLUMNS;
    }

    keysNavigation.add(new PrevKeyCommand(0, 'k', Util.C.changeTablePrev()));
    keysNavigation.add(new NextKeyCommand(0, 'j', Util.C.changeTableNext()));
    keysNavigation.add(new OpenKeyCommand(0, 'o', Util.C.changeTableOpen()));
    keysNavigation.add(new OpenKeyCommand(0, KeyCodes.KEY_ENTER, Util.C
        .changeTableOpen()));

    if (Gerrit.isSignedIn()) {
      keysAction.add(new StarKeyCommand(0, 's', Util.C.changeTableStar()));
    }

    sections = new ArrayList<Section>();
    table.setText(0, C_STAR, "");
    table.setText(0, C_ID, Util.C.changeTableColumnID());
    table.setText(0, C_SUBJECT, Util.C.changeTableColumnSubject());
    table.setText(0, C_OWNER, Util.C.changeTableColumnOwner());
    table.setText(0, C_PROJECT, Util.C.changeTableColumnProject());
    table.setText(0, C_BRANCH, Util.C.changeTableColumnBranch());
    table.setText(0, C_LAST_UPDATE, Util.C.changeTableColumnLastUpdate());
    for (int i = BASE_COLUMNS; i < columns; i++) {
      final ApprovalType type = approvalTypes.get(i - BASE_COLUMNS);
      final ApprovalCategory cat = type.getCategory();
      String text = cat.getAbbreviatedName();
      if (text == null) {
        text = cat.getName();
      }
      table.setText(0, i, text);
      if (text != null) {
        table.getCellFormatter().getElement(0, i).setTitle(cat.getName());
      }
    }

    final FlexCellFormatter fmt = table.getFlexCellFormatter();
    fmt.addStyleName(0, C_STAR, Gerrit.RESOURCES.css().iconHeader());
    fmt.addStyleName(0, C_ID, Gerrit.RESOURCES.css().cID());
    for (int i = C_ID; i < columns; i++) {
      fmt.addStyleName(0, i, Gerrit.RESOURCES.css().dataHeader());
    }

    table.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        final Cell cell = table.getCellForEvent(event);
        if (cell == null) {
          return;
        }
        if (cell.getCellIndex() == C_STAR) {
          onStarClick(cell.getRowIndex());
        } else if (cell.getCellIndex() == C_OWNER) {
          // Don't do anything.
        } else if (getRowItem(cell.getRowIndex()) != null) {
          movePointerTo(cell.getRowIndex());
        }
      }
    });
  }

  protected void onStarClick(final int row) {
    final ChangeInfo c = getRowItem(row);
    if (c != null && Gerrit.isSignedIn()) {
      final boolean prior = c.isStarred();
      c.setStarred(!prior);
      setStar(row, c);

      final ToggleStarRequest req = new ToggleStarRequest();
      req.toggle(c.getId(), c.isStarred());
      Util.LIST_SVC.toggleStars(req, new GerritCallback<VoidResult>() {
        public void onSuccess(final VoidResult result) {
        }

        @Override
        public void onFailure(final Throwable caught) {
          super.onFailure(caught);
          c.setStarred(prior);
          setStar(row, c);
        }
      });
    }
  }

  @Override
  protected Object getRowItemKey(final ChangeInfo item) {
    return item.getId();
  }

  @Override
  protected void onOpenRow(final int row) {
    final ChangeInfo c = getRowItem(row);
    Gerrit.display(PageLinks.toChange(c), new ChangeScreen(c));
  }

  private void insertNoneRow(final int row) {
    insertRow(row);
    table.setText(row, 0, Util.C.changeTableNone());
    final FlexCellFormatter fmt = table.getFlexCellFormatter();
    fmt.setColSpan(row, 0, columns);
    fmt.setStyleName(row, 0, Gerrit.RESOURCES.css().emptySection());
  }

  private void insertChangeRow(final int row) {
    insertRow(row);
    applyDataRowStyle(row);
  }

  @Override
  protected void applyDataRowStyle(final int row) {
    super.applyDataRowStyle(row);
    final CellFormatter fmt = table.getCellFormatter();
    fmt.addStyleName(row, C_STAR, Gerrit.RESOURCES.css().iconCell());
    for (int i = C_ID; i < columns; i++) {
      fmt.addStyleName(row, i, Gerrit.RESOURCES.css().dataCell());
    }
    fmt.addStyleName(row, C_ID, Gerrit.RESOURCES.css().cID());
    fmt.addStyleName(row, C_SUBJECT, Gerrit.RESOURCES.css().cSUBJECT());
    fmt.addStyleName(row, C_PROJECT, Gerrit.RESOURCES.css().cPROJECT());
    fmt.addStyleName(row, C_BRANCH, Gerrit.RESOURCES.css().cPROJECT());
    fmt.addStyleName(row, C_LAST_UPDATE, Gerrit.RESOURCES.css().cLastUpdate());
    for (int i = BASE_COLUMNS; i < columns; i++) {
      fmt.addStyleName(row, i, Gerrit.RESOURCES.css().cAPPROVAL());
    }
  }

  private void populateChangeRow(final int row, final ChangeInfo c) {
    final String idstr = c.getKey().abbreviate();
    table.setWidget(row, C_ARROW, null);
    if (Gerrit.isSignedIn()) {
      setStar(row, c);
    }
    table.setWidget(row, C_ID, new TableChangeLink(idstr, c));

    String s = c.getSubject();
    if (s.length() > 80) {
      s = s.substring(0, 80);
    }
    if (c.getStatus() != null && c.getStatus() != Change.Status.NEW) {
      s += " (" + c.getStatus().name() + ")";
    }
    if (! c.isLatest()) {
      s += " [OUTDATED]";
      table.getRowFormatter().addStyleName(row, Gerrit.RESOURCES.css().outdated());
    }
    table.setWidget(row, C_SUBJECT, new TableChangeLink(s, c));
    table.setWidget(row, C_OWNER, link(c.getOwner()));
    table.setWidget(row, C_PROJECT, new ProjectLink(c.getProject().getKey(), c
        .getStatus()));
    table.setWidget(row, C_BRANCH, new BranchTopicLink(c.getProject().getKey(), c
        .getStatus(), c.getBranch(), c.getTopic(), c.getTopicId()));
    table.setText(row, C_LAST_UPDATE, shortFormat(c.getLastUpdatedOn()));
    setRowItem(row, c);
  }

  private AccountDashboardLink link(final Account.Id id) {
    return AccountDashboardLink.link(accountCache, id);
  }

  private void setStar(final int row, final ChangeInfo c) {
    final ImageResource star;
    if (c.isStarred()) {
      star = Gerrit.RESOURCES.starFilled();
    } else {
      star = Gerrit.RESOURCES.starOpen();
    }

    final Widget i = table.getWidget(row, C_STAR);
    if (i instanceof Image) {
      ((Image) i).setResource(star);
    } else {
      table.setWidget(row, C_STAR, new Image(star));
    }
  }

  public void addSection(final Section s) {
    assert s.parent == null;

    if (s.titleText != null) {
      s.titleRow = table.getRowCount();
      table.setText(s.titleRow, 0, s.titleText);
      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      fmt.setColSpan(s.titleRow, 0, columns);
      fmt.addStyleName(s.titleRow, 0, Gerrit.RESOURCES.css().sectionHeader());
    } else {
      s.titleRow = -1;
    }

    s.parent = this;
    s.dataBegin = table.getRowCount();
    insertNoneRow(s.dataBegin);
    sections.add(s);
  }

  public void setAccountInfoCache(final AccountInfoCache aic) {
    assert aic != null;
    accountCache = aic;
  }

  private int insertRow(final int beforeRow) {
    for (final Section s : sections) {
      if (beforeRow <= s.titleRow) {
        s.titleRow++;
      }
      if (beforeRow < s.dataBegin) {
        s.dataBegin++;
      }
    }
    return table.insertRow(beforeRow);
  }

  private void removeRow(final int row) {
    for (final Section s : sections) {
      if (row < s.titleRow) {
        s.titleRow--;
      }
      if (row < s.dataBegin) {
        s.dataBegin--;
      }
    }
    table.removeRow(row);
  }

  private void displayApprovals(final int row, final ApprovalSummary summary,
      final AccountInfoCache aic, final boolean highlightUnreviewed) {
    final CellFormatter fmt = table.getCellFormatter();
    final Map<ApprovalCategory.Id, PatchSetApproval> approvals =
        summary.getApprovalMap();
    int col = BASE_COLUMNS;
    boolean haveReview = false;

    boolean displayPersonNameInReviewCategory = false;

    if (Gerrit.isSignedIn()) {
      AccountGeneralPreferences prefs = Gerrit.getUserAccount().getGeneralPreferences();

      if (prefs.isDisplayPersonNameInReviewCategory()) {
        displayPersonNameInReviewCategory = true;
      }
    }

    for (final ApprovalType type : approvalTypes) {
      final PatchSetApproval ca = approvals.get(type.getCategory().getId());

      fmt.removeStyleName(row, col, Gerrit.RESOURCES.css().negscore());
      fmt.removeStyleName(row, col, Gerrit.RESOURCES.css().posscore());
      fmt.addStyleName(row, col, Gerrit.RESOURCES.css().singleLine());

      if (ca == null || ca.getValue() == 0) {
        table.clearCell(row, col);

      } else {
        haveReview = true;

        final ApprovalCategoryValue acv = type.getValue(ca);
        final AccountInfo ai = aic.get(ca.getAccountId());

        if (type.isMaxNegative(ca)) {

          if (displayPersonNameInReviewCategory) {
            FlowPanel fp = new FlowPanel();
            fp.add(new Image(Gerrit.RESOURCES.redNot()));
            fp.add(new InlineLabel(FormatUtil.name(ai)));
            table.setWidget(row, col, fp);
          } else {
            table.setWidget(row, col, new Image(Gerrit.RESOURCES.redNot()));
          }

        } else if (type.isMaxPositive(ca)) {

          if (displayPersonNameInReviewCategory) {
            FlowPanel fp = new FlowPanel();
            fp.add(new Image(Gerrit.RESOURCES.greenCheck()));
            fp.add(new InlineLabel(FormatUtil.name(ai)));
            table.setWidget(row, col, fp);
          } else {
            table.setWidget(row, col, new Image(Gerrit.RESOURCES.greenCheck()));
          }

        } else {
          String vstr = String.valueOf(ca.getValue());

          if (displayPersonNameInReviewCategory) {
            vstr = vstr + " " + FormatUtil.name(ai);
          }

          if (ca.getValue() > 0) {
            vstr = "+" + vstr;
            fmt.addStyleName(row, col, Gerrit.RESOURCES.css().posscore());
          } else {
            fmt.addStyleName(row, col, Gerrit.RESOURCES.css().negscore());
          }
          table.setText(row, col, vstr);
        }

        // Some web browsers ignore the embedded newline; some like it;
        // so we include a space before the newline to accommodate both.
        //
        fmt.getElement(row, col).setTitle(
            acv.getName() + " \nby " + FormatUtil.nameEmail(ai));
      }

      col++;
    }

    final Element tr = DOM.getParent(fmt.getElement(row, 0));
    UIObject.setStyleName(tr, Gerrit.RESOURCES.css().needsReview(), !haveReview
        && highlightUnreviewed);
  }

  GerritCallback<ApprovalSummarySet> approvalFormatter(final int dataBegin,
      final int rows, final boolean highlightUnreviewed) {
    return new GerritCallback<ApprovalSummarySet>() {
      @Override
      public void onSuccess(final ApprovalSummarySet as) {
        Map<Change.Id, ApprovalSummary> ids = as.getSummaryMap();
        AccountInfoCache aic = as.getAccountInfoCache();
        for (int row = dataBegin; row < dataBegin + rows; row++) {
          final ChangeInfo c = getRowItem(row);
          if (ids.containsKey(c.getId())) {
            displayApprovals(row, ids.get(c.getId()), aic, highlightUnreviewed);
          }
        }
      }
    };
  }

  public class StarKeyCommand extends NeedsSignInKeyCommand {
    public StarKeyCommand(int mask, char key, String help) {
      super(mask, key, help);
    }

    @Override
    public void onKeyPress(final KeyPressEvent event) {
      onStarClick(getCurrentRow());
    }
  }

  private final class TableChangeLink extends ChangeLink {
    private TableChangeLink(final String text, final ChangeInfo c) {
      super(text, c);
    }

    @Override
    public void go() {
      movePointerTo(cid);
      super.go();
    }
  }

  public enum ApprovalViewType {
    NONE, USER, STRONGEST
  }

  public static class Section {
    String titleText;

    ChangeTable parent;
    final ApprovalViewType viewType;
    final Account.Id ownerId;
    int titleRow = -1;
    int dataBegin;
    int rows;

    public Section() {
      this(null, ApprovalViewType.NONE, null);
    }

    public Section(final String titleText) {
      this(titleText, ApprovalViewType.NONE, null);
    }

    public Section(final String titleText, final ApprovalViewType view,
        final Account.Id owner) {
      setTitleText(titleText);
      viewType = view;
      ownerId = owner;
    }

    public void setTitleText(final String text) {
      titleText = text;
      if (titleRow >= 0) {
        parent.table.setText(titleRow, 0, titleText);
      }
    }

    public void display(final List<ChangeInfo> changeList) {
      final int sz = changeList != null ? changeList.size() : 0;
      final boolean hadData = rows > 0;

      if (hadData) {
        while (sz < rows) {
          parent.removeRow(dataBegin);
          rows--;
        }
      }

      if (sz == 0) {
        if (hadData) {
          parent.insertNoneRow(dataBegin);
        }
      } else {
        Set<Change.Id> cids = new HashSet<Change.Id>();

        if (!hadData) {
          parent.removeRow(dataBegin);
        }

        while (rows < sz) {
          parent.insertChangeRow(dataBegin + rows);
          rows++;
        }

        for (int i = 0; i < sz; i++) {
          ChangeInfo c = changeList.get(i);
          parent.populateChangeRow(dataBegin + i, c);
          cids.add(c.getId());
        }

        switch (viewType) {
          case NONE:
            break;
          case USER:
            PatchUtil.DETAIL_SVC.userApprovals(cids, ownerId, parent
                .approvalFormatter(dataBegin, rows, true));
            break;
          case STRONGEST:
            PatchUtil.DETAIL_SVC.strongestApprovals(cids, parent
                .approvalFormatter(dataBegin, rows, false));
            break;
        }
      }
    }
  }
}
