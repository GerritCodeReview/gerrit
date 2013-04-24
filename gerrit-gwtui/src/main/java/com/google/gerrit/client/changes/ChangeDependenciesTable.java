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

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.ui.AccountLink;
import com.google.gerrit.client.ui.BranchLink;
import com.google.gerrit.client.ui.ChangeLink;
import com.google.gerrit.client.ui.NavigationTable;
import com.google.gerrit.client.ui.NeedsSignInKeyCommand;
import com.google.gerrit.client.ui.ProjectLink;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.common.data.AccountInfoCache;
import com.google.gerrit.common.data.ChangeInfo;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;
import com.google.gwt.user.client.ui.HTMLTable.Cell;
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ChangeDependenciesTable extends NavigationTable<ChangeInfo> {
  private static final int C_STAR = 1;
  private static final int C_SUBJECT = 2;
  private static final int C_OWNER = 3;
  private static final int C_PROJECT = 4;
  private static final int C_BRANCH = 5;
  private static final int C_LAST_UPDATE = 6;
  private static final int COLUMNS = 7;

  private final List<Section> sections;
  private AccountInfoCache accountCache = AccountInfoCache.empty();

  public ChangeDependenciesTable() {
    super(Util.C.changeItemHelp());

    if (Gerrit.isSignedIn()) {
      keysAction.add(new StarKeyCommand(0, 's', Util.C.changeTableStar()));
    }

    sections = new ArrayList<Section>();
    table.setText(0, C_STAR, "");
    table.setText(0, C_SUBJECT, Util.C.changeTableColumnSubject());
    table.setText(0, C_OWNER, Util.C.changeTableColumnOwner());
    table.setText(0, C_PROJECT, Util.C.changeTableColumnProject());
    table.setText(0, C_BRANCH, Util.C.changeTableColumnBranch());
    table.setText(0, C_LAST_UPDATE, Util.C.changeTableColumnLastUpdate());

    final FlexCellFormatter fmt = table.getFlexCellFormatter();
    fmt.addStyleName(0, C_STAR, Gerrit.RESOURCES.css().iconHeader());
    for (int i = C_SUBJECT; i < COLUMNS; i++) {
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
          // Don't do anything (handled by star itself).
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
      ((StarredChanges.Icon) table.getWidget(row, C_STAR)).toggleStar();
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
    fmt.setColSpan(row, 0, COLUMNS);
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
    for (int i = C_SUBJECT; i < COLUMNS; i++) {
      fmt.addStyleName(row, i, Gerrit.RESOURCES.css().dataCell());
    }
    fmt.addStyleName(row, C_SUBJECT, Gerrit.RESOURCES.css().cSUBJECT());
    fmt.addStyleName(row, C_OWNER, Gerrit.RESOURCES.css().cOWNER());
    fmt.addStyleName(row, C_LAST_UPDATE, Gerrit.RESOURCES.css().cLastUpdate());
  }

  private void populateChangeRow(final int row, final ChangeInfo c,
      final ChangeRowFormatter changeRowFormatter) {
    ChangeCache cache = ChangeCache.get(c.getId());
    cache.getChangeInfoCache().set(c);

    table.setWidget(row, C_ARROW, null);
    if (Gerrit.isSignedIn()) {
      table.setWidget(row, C_STAR, StarredChanges.createIcon(c.getId(), c.isStarred()));
    }

    String s = Util.cropSubject(c.getSubject());
    if (c.getStatus() != null && c.getStatus() != Change.Status.NEW) {
      s += " (" + c.getStatus().name() + ")";
    }
    if (changeRowFormatter != null) {
      removeChangeStyle(row, changeRowFormatter);
      final String rowStyle = changeRowFormatter.getRowStyle(c);
      if (rowStyle != null) {
        table.getRowFormatter().addStyleName(row, rowStyle);
      }
      s = changeRowFormatter.getDisplayText(c, s);
    }

    table.setWidget(row, C_SUBJECT, new TableChangeLink(s, c));
    table.setWidget(row, C_OWNER, link(c.getOwner()));
    table.setWidget(row, C_PROJECT, new ProjectLink(c.getProject().getKey(), c
        .getStatus()));
    table.setWidget(row, C_BRANCH, new BranchLink(c.getProject().getKey(), c
        .getStatus(), c.getBranch(), c.getTopic()));
    table.setText(row, C_LAST_UPDATE, shortFormat(c.getLastUpdatedOn()));

    setRowItem(row, c);
  }

  private void removeChangeStyle(int row,
      final ChangeRowFormatter changeRowFormatter) {
    final ChangeInfo oldChange = getRowItem(row);
    if (oldChange == null) {
      return;
    }

    final String oldRowStyle = changeRowFormatter.getRowStyle(oldChange);
    if (oldRowStyle != null) {
      table.getRowFormatter().removeStyleName(row, oldRowStyle);
    }
  }

  private AccountLink link(final Account.Id id) {
    return AccountLink.link(accountCache, id);
  }

  public void addSection(final Section s) {
    assert s.parent == null;

    if (s.titleText != null) {
      s.titleRow = table.getRowCount();
      table.setText(s.titleRow, 0, s.titleText);
      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      fmt.setColSpan(s.titleRow, 0, COLUMNS);
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

  public static class Section {
    String titleText;

    ChangeDependenciesTable parent;
    final Account.Id ownerId;
    int titleRow = -1;
    int dataBegin;
    int rows;

    private ChangeRowFormatter changeRowFormatter;

    public Section() {
      this(null, null);
    }

    public Section(final String titleText) {
      this(titleText, null);
    }

    public Section(final String titleText, final Account.Id owner) {
      setTitleText(titleText);
      ownerId = owner;
    }

    public void setTitleText(final String text) {
      titleText = text;
      if (titleRow >= 0) {
        parent.table.setText(titleRow, 0, titleText);
      }
    }

    public void setChangeRowFormatter(final ChangeRowFormatter changeRowFormatter) {
      this.changeRowFormatter = changeRowFormatter;
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
          parent.populateChangeRow(dataBegin + i, c, changeRowFormatter);
          cids.add(c.getId());
        }
      }
    }
  }

  public static interface ChangeRowFormatter {
    /**
     * Returns the name of the CSS style that should be applied to the change
     * row.
     *
     * @param c the change for which the styling should be returned
     * @return the name of the CSS style that should be applied to the change
     *         row
     */
    String getRowStyle(ChangeInfo c);

    /**
     * Returns the text that should be displayed for the change.
     *
     * @param c the change for which the display text should be returned
     * @param displayText the current display text
     * @return the new display text
     */
    String getDisplayText(ChangeInfo c, String displayText);
  }
}
