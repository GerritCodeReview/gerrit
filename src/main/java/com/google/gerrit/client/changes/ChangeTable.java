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
import com.google.gerrit.client.Link;
import com.google.gerrit.client.data.AccountInfo;
import com.google.gerrit.client.data.AccountInfoCache;
import com.google.gerrit.client.data.ApprovalSummary;
import com.google.gerrit.client.data.ApprovalSummarySet;
import com.google.gerrit.client.data.ApprovalType;
import com.google.gerrit.client.data.ChangeInfo;
import com.google.gerrit.client.patches.PatchUtil;
import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.ApprovalCategory;
import com.google.gerrit.client.reviewdb.ApprovalCategoryValue;
import com.google.gerrit.client.reviewdb.Change;
import com.google.gerrit.client.reviewdb.PatchSetApproval;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.AccountDashboardLink;
import com.google.gerrit.client.ui.ChangeLink;
import com.google.gerrit.client.ui.NavigationTable;
import com.google.gerrit.client.ui.NeedsSignInKeyCommand;
import com.google.gerrit.client.ui.ProjectLink;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.AbstractImagePrototype;
import com.google.gwt.user.client.ui.Image;
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
  private static final String S_C_ID = "C_ID";
  private static final String S_C_SUBJECT = "C_SUBJECT";
  private static final String S_C_PROJECT = "C_PROJECT";
  private static final String S_C_LAST_UPDATE = "C_LAST_UPDATE";
  private static final String S_C_APPROVAL = "C_APPROVAL";
  private static final String S_SECTION_HEADER = "SectionHeader";
  private static final String S_EMPTY_SECTION = "EmptySection";
  private static final String S_NEEDS_REVIEW = "NeedsReview";

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
    fmt.addStyleName(0, C_STAR, S_ICON_HEADER);
    fmt.addStyleName(0, C_ID, S_C_ID);
    for (int i = C_ID; i < columns; i++) {
      fmt.addStyleName(0, i, S_DATA_HEADER);
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
    Gerrit.display(Link.toChange(c), new ChangeScreen(c));
  }

  private void insertNoneRow(final int row) {
    insertRow(row);
    table.setText(row, 0, Util.C.changeTableNone());
    final FlexCellFormatter fmt = table.getFlexCellFormatter();
    fmt.setColSpan(row, 0, columns);
    fmt.setStyleName(row, 0, S_EMPTY_SECTION);
  }

  private void insertChangeRow(final int row) {
    insertRow(row);
    applyDataRowStyle(row);
  }

  @Override
  protected void applyDataRowStyle(final int row) {
    super.applyDataRowStyle(row);
    final CellFormatter fmt = table.getCellFormatter();
    fmt.addStyleName(row, C_STAR, S_ICON_CELL);
    for (int i = C_ID; i < columns; i++) {
      fmt.addStyleName(row, i, S_DATA_CELL);
    }
    fmt.addStyleName(row, C_ID, S_C_ID);
    fmt.addStyleName(row, C_SUBJECT, S_C_SUBJECT);
    fmt.addStyleName(row, C_PROJECT, S_C_PROJECT);
    fmt.addStyleName(row, C_BRANCH, S_C_PROJECT);
    fmt.addStyleName(row, C_LAST_UPDATE, S_C_LAST_UPDATE);
    for (int i = BASE_COLUMNS; i < columns; i++) {
      fmt.addStyleName(row, i, S_C_APPROVAL);
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
    table.setWidget(row, C_SUBJECT, new TableChangeLink(s, c));
    table.setWidget(row, C_OWNER, link(c.getOwner()));
    table.setWidget(row, C_PROJECT,
        new ProjectLink(c.getProject().getKey(), c.getStatus()));
    table.setText(row, C_BRANCH, c.getBranch());
    table.setText(row, C_LAST_UPDATE, shortFormat(c.getLastUpdatedOn()));
    setRowItem(row, c);
  }

  private AccountDashboardLink link(final Account.Id id) {
    return AccountDashboardLink.link(accountCache, id);
  }

  private void setStar(final int row, final ChangeInfo c) {
    final AbstractImagePrototype star;
    if (c.isStarred()) {
      star = Gerrit.ICONS.starFilled();
    } else {
      star = Gerrit.ICONS.starOpen();
    }

    final Widget i = table.getWidget(row, C_STAR);
    if (i instanceof Image) {
      star.applyTo((Image) i);
    } else {
      table.setWidget(row, C_STAR, star.createImage());
    }
  }

  public void addSection(final Section s) {
    assert s.parent == null;

    if (s.titleText != null) {
      s.titleRow = table.getRowCount();
      table.setText(s.titleRow, 0, s.titleText);
      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      fmt.setColSpan(s.titleRow, 0, columns);
      fmt.addStyleName(s.titleRow, 0, S_SECTION_HEADER);
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

    for (final ApprovalType type : approvalTypes) {
      final PatchSetApproval ca = approvals.get(type.getCategory().getId());

      fmt.removeStyleName(row, col, "negscore");
      fmt.removeStyleName(row, col, "posscore");

      if (ca == null || ca.getValue() == 0) {
        table.clearCell(row, col);

      } else {
        haveReview = true;

        if (type.isMaxNegative(ca)) {
          table.setWidget(row, col, Gerrit.ICONS.redNot().createImage());

        } else if (type.isMaxPositive(ca)) {
          table.setWidget(row, col, Gerrit.ICONS.greenCheck().createImage());

        } else {
          String vstr = String.valueOf(ca.getValue());
          if (ca.getValue() > 0) {
            vstr = "+" + vstr;
            fmt.addStyleName(row, col, "posscore");
          } else {
            fmt.addStyleName(row, col, "negscore");
          }
          table.setText(row, col, vstr);
        }

        final ApprovalCategoryValue acv = type.getValue(ca);
        final AccountInfo ai = aic.get(ca.getAccountId());

        // Some web browsers ignore the embedded newline; some like it;
        // so we include a space before the newline to accommodate both.
        //
        fmt.getElement(row, col).setTitle(
            acv.getName() + " \nby " + FormatUtil.nameEmail(ai));
      }

      col++;
    }

    final Element tr = DOM.getParent(fmt.getElement(row, 0));
    UIObject.setStyleName(tr, S_NEEDS_REVIEW, !haveReview
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
      movePointerTo(id);
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
