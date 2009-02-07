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

import static com.google.gerrit.client.FormatUtil.mediumFormat;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.Link;
import com.google.gerrit.client.SignedInListener;
import com.google.gerrit.client.data.AccountInfoCache;
import com.google.gerrit.client.data.ChangeInfo;
import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.Change;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.AccountDashboardLink;
import com.google.gerrit.client.ui.ChangeLink;
import com.google.gerrit.client.ui.FancyFlexTable;
import com.google.gerrit.client.ui.ProjectOpenLink;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.AbstractImagePrototype;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.SourcesTableEvents;
import com.google.gwt.user.client.ui.TableListener;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;
import com.google.gwtjsonrpc.client.VoidResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ChangeTable extends FancyFlexTable<ChangeInfo> {
  private static final String S_C_ID = "C_ID";
  private static final String S_C_SUBJECT = "C_SUBJECT";
  private static final String S_C_PROJECT = "C_PROJECT";
  private static final String S_C_LAST_UPDATE = "C_LAST_UPDATE";
  private static final String S_SECTION_HEADER = "SectionHeader";
  private static final String S_EMPTY_SECTION = "EmptySection";

  private static final int C_STAR = 1;
  private static final int C_ID = 2;
  private static final int C_SUBJECT = 3;
  private static final int C_OWNER = 4;
  private static final int C_PROJECT = 5;
  private static final int C_LAST_UPDATE = 6;
  private static final int COLUMNS = 7;

  private final List<Section> sections;
  private final SignedInListener signedInListener;
  private AccountInfoCache accountCache = AccountInfoCache.empty();

  public ChangeTable() {
    sections = new ArrayList<Section>();
    table.setText(0, C_STAR, "");
    table.setText(0, C_ID, Util.C.changeTableColumnID());
    table.setText(0, C_SUBJECT, Util.C.changeTableColumnSubject());
    table.setText(0, C_OWNER, Util.C.changeTableColumnOwner());
    table.setText(0, C_PROJECT, Util.C.changeTableColumnProject());
    table.setText(0, C_LAST_UPDATE, Util.C.changeTableColumnLastUpdate());

    final FlexCellFormatter fmt = table.getFlexCellFormatter();
    fmt.addStyleName(0, C_STAR, S_ICON_HEADER);
    fmt.addStyleName(0, C_ID, S_C_ID);
    for (int i = C_ID; i < COLUMNS; i++) {
      fmt.addStyleName(0, i, S_DATA_HEADER);
    }

    table.addTableListener(new TableListener() {
      public void onCellClicked(SourcesTableEvents sender, int row, int cell) {
        if (cell == C_STAR) {
          onStarClick(row);
        } else if (cell == C_OWNER) {
          // Don't do anything.
        } else if (getRowItem(row) != null) {
          movePointerTo(row);
        }
      }
    });

    signedInListener = new SignedInListener() {
      public void onSignIn() {
        if (table.getRowCount() <= sections.size()) {
          // There are no data rows in this table, so star status is
          // simply not relevant to the caller.
          //
          return;
        }

        Util.LIST_SVC.myStarredChangeIds(new GerritCallback<Set<Change.Id>>() {
          public void onSuccess(final Set<Change.Id> result) {
            final FlexCellFormatter fmt = table.getFlexCellFormatter();
            final int max = table.getRowCount();
            for (int row = 0; row < max; row++) {
              final ChangeInfo c = getRowItem(row);
              if (c != null) {
                c.setStarred(result.contains(c.getId()));
                setStar(row, c);
              }
            }
          }
        });
      }

      public void onSignOut() {
        final FlexCellFormatter fmt = table.getFlexCellFormatter();
        final int max = table.getRowCount();
        for (int row = 0; row < max; row++) {
          if (getRowItem(row) != null) {
            table.clearCell(row, C_STAR);
          }
        }
      }
    };
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
  protected boolean onKeyPress(final char keyCode, final int modifiers) {
    if (super.onKeyPress(keyCode, modifiers)) {
      return true;
    }
    if (modifiers == 0) {
      switch (keyCode) {
        case 's':
          onStarClick(getCurrentRow());
          return true;
      }
    }
    return false;
  }

  @Override
  protected void onOpenItem(final ChangeInfo c) {
    Gerrit.display(Link.toChange(c), new ChangeScreen(c));
  }

  @Override
  public void onLoad() {
    super.onLoad();
    Gerrit.addSignedInListener(signedInListener);
  }

  @Override
  public void onUnload() {
    Gerrit.removeSignedInListener(signedInListener);
    super.onUnload();
  }

  private void insertNoneRow(final int row) {
    insertRow(row);
    table.setText(row, 0, Util.C.changeTableNone());
    final FlexCellFormatter fmt = table.getFlexCellFormatter();
    fmt.setColSpan(row, 0, COLUMNS);
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
    for (int i = C_ID; i < COLUMNS; i++) {
      fmt.addStyleName(row, i, S_DATA_CELL);
    }
    fmt.addStyleName(row, C_ID, S_C_ID);
    fmt.addStyleName(row, C_SUBJECT, S_C_SUBJECT);
    fmt.addStyleName(row, C_PROJECT, S_C_PROJECT);
    fmt.addStyleName(row, C_LAST_UPDATE, S_C_LAST_UPDATE);
  }

  private void populateChangeRow(final int row, final ChangeInfo c) {
    final String idstr = String.valueOf(c.getId().get());
    table.setWidget(row, C_ARROW, null);
    if (Gerrit.isSignedIn()) {
      setStar(row, c);
    }
    table.setWidget(row, C_ID, new TableChangeLink(idstr, c));

    String s = c.getSubject();
    if (c.getStatus() != null && c.getStatus() != Change.Status.NEW) {
      s += " (" + c.getStatus().name() + ")";
    }
    table.setWidget(row, C_SUBJECT, new TableChangeLink(s, c));
    table.setWidget(row, C_OWNER, link(c.getOwner()));
    table.setWidget(row, C_PROJECT,
        new ProjectOpenLink(c.getProject().getKey()));
    table.setText(row, C_LAST_UPDATE, mediumFormat(c.getLastUpdatedOn()));
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
      fmt.setColSpan(s.titleRow, 0, COLUMNS);
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
      boolean dirty = false;
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

  private final class TableChangeLink extends ChangeLink {
    private TableChangeLink(final String text, final ChangeInfo c) {
      super(text, c);
    }

    @Override
    protected void onClick(final Event event) {
      movePointerTo(id);
      super.onClick(event);
    }
  }

  public static class Section {
    String titleText;

    ChangeTable parent;
    int titleRow = -1;
    int dataBegin;
    int rows;

    public Section() {
      this(null);
    }

    public Section(final String titleText) {
      setTitleText(titleText);
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
        if (!hadData) {
          parent.removeRow(dataBegin);
        }

        while (rows < sz) {
          parent.insertChangeRow(dataBegin + rows);
          rows++;
        }
        for (int i = 0; i < sz; i++) {
          parent.populateChangeRow(dataBegin + i, changeList.get(i));
        }
      }
    }
  }
}
