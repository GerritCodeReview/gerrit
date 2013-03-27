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

import static com.google.gerrit.client.FormatUtil.relativeFormat;
import static com.google.gerrit.client.FormatUtil.shortFormat;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.changes.ChangeInfo.LabelInfo;
import com.google.gerrit.client.ui.AccountLink;
import com.google.gerrit.client.ui.BranchLink;
import com.google.gerrit.client.ui.ChangeLink;
import com.google.gerrit.client.ui.NavigationTable;
import com.google.gerrit.client.ui.NeedsSignInKeyCommand;
import com.google.gerrit.client.ui.ProjectLink;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTMLTable.Cell;
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.UIObject;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChangeTable2 extends NavigationTable<ChangeInfo> {
  private static final int C_STAR = 1;
  private static final int C_SUBJECT = 2;
  private static final int C_OWNER = 3;
  private static final int C_PROJECT = 4;
  private static final int C_BRANCH = 5;
  private static final int C_LAST_UPDATE = 6;
  private static final int BASE_COLUMNS = 7;

  private final List<Section> sections;
  private int columns;
  private List<String> labelNames;

  public ChangeTable2() {
    super(Util.C.changeItemHelp());
    columns = BASE_COLUMNS;
    labelNames = Collections.emptyList();

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
    for (int i = C_SUBJECT; i < columns; i++) {
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

  @Override
  protected Object getRowItemKey(final ChangeInfo item) {
    return item.legacy_id();
  }

  @Override
  protected void onOpenRow(final int row) {
    final ChangeInfo c = getRowItem(row);
    final Change.Id id = c.legacy_id();
    Gerrit.display(PageLinks.toChange(id), new ChangeScreen(id));
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
    for (int i = C_SUBJECT; i < columns; i++) {
      fmt.addStyleName(row, i, Gerrit.RESOURCES.css().dataCell());
    }
    fmt.addStyleName(row, C_SUBJECT, Gerrit.RESOURCES.css().cSUBJECT());
    fmt.addStyleName(row, C_OWNER, Gerrit.RESOURCES.css().cOWNER());
    fmt.addStyleName(row, C_LAST_UPDATE, Gerrit.RESOURCES.css().cLastUpdate());
    for (int i = BASE_COLUMNS; i < columns; i++) {
      fmt.addStyleName(row, i, Gerrit.RESOURCES.css().cAPPROVAL());
    }
  }

  public void updateColumnsForLabels(ChangeList... lists) {
    labelNames = new ArrayList<String>();
    for (ChangeList list : lists) {
      for (int i = 0; i < list.length(); i++) {
        for (String name : list.get(i).labels()) {
          if (!labelNames.contains(name)) {
            labelNames.add(name);
          }
        }
      }
    }
    Collections.sort(labelNames);

    if (BASE_COLUMNS + labelNames.size() < columns) {
      int n = columns - (BASE_COLUMNS + labelNames.size());
      for (int row = 0; row < table.getRowCount(); row++) {
        table.removeCells(row, columns, n);
      }
    }
    columns = BASE_COLUMNS + labelNames.size();

    FlexCellFormatter fmt = table.getFlexCellFormatter();
    for (int i = 0; i < labelNames.size(); i++) {
      String name = labelNames.get(i);
      int col = BASE_COLUMNS + i;

      StringBuilder abbrev = new StringBuilder();
      for (String t : name.split("-")) {
        abbrev.append(t.substring(0, 1).toUpperCase());
      }
      table.setText(0, col, abbrev.toString());
      table.getCellFormatter().getElement(0, col).setTitle(name);
      fmt.addStyleName(0, col, Gerrit.RESOURCES.css().dataHeader());
    }

    for (Section s : sections) {
      if (s.titleRow >= 0) {
        fmt.setColSpan(s.titleRow, 0, columns);
      }
    }
  }

  private void populateChangeRow(final int row, final ChangeInfo c,
      boolean highlightUnreviewed) {
    if (Gerrit.isSignedIn()) {
      table.setWidget(row, C_STAR, StarredChanges.createIcon(
          c.legacy_id(),
          c.starred()));
    }

    String subject = Util.cropSubject(c.subject());
    Change.Status status = c.status();
    if (status != Change.Status.NEW) {
      subject += " (" + Util.toLongString(status) + ")";
    }
    table.setWidget(row, C_SUBJECT, new TableChangeLink(subject, c));

    if (c.owner() != null) {
      table.setWidget(row, C_OWNER, new AccountLink(c.owner(), status));
    } else {
      table.setText(row, C_OWNER, "");
    }

    table.setWidget(
        row, C_PROJECT, new ProjectLink(c.project_name_key(), c.status()));
    table.setWidget(row, C_BRANCH, new BranchLink(c.project_name_key(), c
        .status(), c.branch(), c.topic()));
    if (Gerrit.isSignedIn()
        && Gerrit.getUserAccount().getGeneralPreferences()
            .isRelativeDateInChangeTable()) {
      table.setText(row, C_LAST_UPDATE, relativeFormat(c.updated()));
    } else {
      table.setText(row, C_LAST_UPDATE, shortFormat(c.updated()));
    }

    boolean displayName = Gerrit.isSignedIn() && Gerrit.getUserAccount()
        .getGeneralPreferences().isShowUsernameInReviewCategory();

    CellFormatter fmt = table.getCellFormatter();
    for (int idx = 0; idx < labelNames.size(); idx++) {
      String name = labelNames.get(idx);
      int col = BASE_COLUMNS + idx;

      LabelInfo label = c.label(name);
      if (label == null) {
        table.clearCell(row, col);
        continue;
      }

      String user;
      if (label.rejected() != null) {
        user = label.rejected().name();
        if (displayName && user != null) {
          FlowPanel panel = new FlowPanel();
          panel.add(new Image(Gerrit.RESOURCES.redNot()));
          panel.add(new InlineLabel(user));
          table.setWidget(row, col, panel);
        } else {
          table.setWidget(row, col, new Image(Gerrit.RESOURCES.redNot()));
        }
      } else if (label.approved() != null) {
        user = label.approved().name();
        if (displayName && user != null) {
          FlowPanel panel = new FlowPanel();
          panel.add(new Image(Gerrit.RESOURCES.greenCheck()));
          panel.add(new InlineLabel(user));
          table.setWidget(row, col, panel);
        } else {
          table.setWidget(row, col, new Image(Gerrit.RESOURCES.greenCheck()));
        }
      } else if (label.disliked() != null) {
        user = label.disliked().name();
        String vstr = String.valueOf(label._value());
        if (displayName && user != null) {
          vstr = vstr + " " + user;
        }
        fmt.addStyleName(row, col, Gerrit.RESOURCES.css().negscore());
        table.setText(row, col, vstr);
      } else if (label.recommended() != null) {
        user = label.recommended().name();
        String vstr = "+" + label._value();
        if (displayName && user != null) {
          vstr = vstr + " " + user;
        }
        fmt.addStyleName(row, col, Gerrit.RESOURCES.css().posscore());
        table.setText(row, col, vstr);
      } else {
        table.clearCell(row, col);
        continue;
      }
      fmt.addStyleName(row, col, Gerrit.RESOURCES.css().singleLine());

      if (!displayName && user != null) {
        // Some web browsers ignore the embedded newline; some like it;
        // so we include a space before the newline to accommodate both.
        fmt.getElement(row, col).setTitle(name + " \nby " + user);
      }
    }

    boolean needHighlight = false;
    if (highlightUnreviewed && !c.reviewed()) {
      needHighlight = true;
    }
    final Element tr = DOM.getParent(fmt.getElement(row, 0));
    UIObject.setStyleName(tr, Gerrit.RESOURCES.css().needsReview(),
        needHighlight);

    setRowItem(row, c);
  }

  public void addSection(final Section s) {
    assert s.parent == null;

    s.parent = this;
    s.titleRow = table.getRowCount();
    if (s.displayTitle()) {
      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      fmt.setColSpan(s.titleRow, 0, columns);
      fmt.addStyleName(s.titleRow, 0, Gerrit.RESOURCES.css().sectionHeader());
    } else {
      s.titleRow = -1;
    }

    s.dataBegin = table.getRowCount();
    insertNoneRow(s.dataBegin);
    sections.add(s);
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
      int row = getCurrentRow();
      ChangeInfo c = getRowItem(row);
      if (c != null && Gerrit.isSignedIn()) {
        ((StarredChanges.Icon) table.getWidget(row, C_STAR)).toggleStar();
      }
    }
  }

  private final class TableChangeLink extends ChangeLink {
    private TableChangeLink(final String text, final ChangeInfo c) {
      super(text, c.legacy_id());
    }

    @Override
    public void go() {
      movePointerTo(cid);
      super.go();
    }
  }

  public static class Section {
    ChangeTable2 parent;
    String titleText;
    Widget titleWidget;
    int titleRow = -1;
    int dataBegin;
    int rows;
    private boolean highlightUnreviewed;

    public void setHighlightUnreviewed(boolean value) {
      this.highlightUnreviewed = value;
    }

    public void setTitleText(final String text) {
      titleText = text;
      titleWidget = null;
      if (titleRow >= 0) {
        parent.table.setText(titleRow, 0, titleText);
      }
    }

    public void setTitleWidget(final Widget title) {
      titleWidget = title;
      titleText = null;
      if (titleRow >= 0) {
        parent.table.setWidget(titleRow, 0, title);
      }
    }

    public boolean displayTitle() {
      if (titleText != null) {
        setTitleText(titleText);
        return true;
      } else if(titleWidget != null) {
        setTitleWidget(titleWidget);
        return true;
      }
      return false;
    }

    public void display(ChangeList changeList) {
      final int sz = changeList != null ? changeList.length() : 0;
      final boolean hadData = rows > 0;

      if (hadData) {
        while (sz < rows) {
          parent.removeRow(dataBegin);
          rows--;
        }
      } else {
        parent.removeRow(dataBegin);
      }

      if (sz == 0) {
        parent.insertNoneRow(dataBegin);
        return;
      }

      while (rows < sz) {
        parent.insertChangeRow(dataBegin + rows);
        rows++;
      }
      for (int i = 0; i < sz; i++) {
        parent.populateChangeRow(dataBegin + i, changeList.get(i),
            highlightUnreviewed);
      }
    }
  }
}
