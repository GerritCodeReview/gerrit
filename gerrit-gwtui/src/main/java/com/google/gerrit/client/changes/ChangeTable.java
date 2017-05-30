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
import com.google.gerrit.client.info.AccountInfo;
import com.google.gerrit.client.info.ChangeInfo;
import com.google.gerrit.client.info.ChangeInfo.LabelInfo;
import com.google.gerrit.client.ui.AccountLinkPanel;
import com.google.gerrit.client.ui.BranchLink;
import com.google.gerrit.client.ui.ChangeLink;
import com.google.gerrit.client.ui.NavigationTable;
import com.google.gerrit.client.ui.NeedsSignInKeyCommand;
import com.google.gerrit.client.ui.ProjectLink;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.ReviewCategoryStrategy;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTMLTable.Cell;
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.UIObject;
import com.google.gwt.user.client.ui.Widget;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class ChangeTable extends NavigationTable<ChangeInfo> {
  // If changing default options, also update in
  // ChangeIT#defaultSearchDoesNotTouchDatabase().
  static final Set<ListChangesOption> OPTIONS =
      Collections.unmodifiableSet(
          EnumSet.of(ListChangesOption.LABELS, ListChangesOption.DETAILED_ACCOUNTS));

  private static final int C_STAR = 1;
  private static final int C_ID = 2;
  private static final int C_SUBJECT = 3;
  private static final int C_STATUS = 4;
  private static final int C_OWNER = 5;
  private static final int C_ASSIGNEE = 6;
  private static final int C_PROJECT = 7;
  private static final int C_BRANCH = 8;
  private static final int C_LAST_UPDATE = 9;
  private static final int C_SIZE = 10;
  private static final int BASE_COLUMNS = 11;

  private final List<Section> sections;
  private int columns;
  private final boolean showAssignee;
  private final boolean showLegacyId;
  private List<String> labelNames;

  public ChangeTable() {
    super(Util.C.changeItemHelp());
    columns = BASE_COLUMNS;
    labelNames = Collections.emptyList();
    showAssignee = Gerrit.info().change().showAssigneeInChangesTable();
    showLegacyId = Gerrit.getUserPreferences().legacycidInChangeTable();

    if (Gerrit.isSignedIn()) {
      keysAction.add(new StarKeyCommand(0, 's', Util.C.changeTableStar()));
    }

    sections = new ArrayList<>();
    table.setText(0, C_STAR, "");
    table.setText(0, C_ID, Util.C.changeTableColumnID());
    table.setText(0, C_SUBJECT, Util.C.changeTableColumnSubject());
    table.setText(0, C_STATUS, Util.C.changeTableColumnStatus());
    table.setText(0, C_OWNER, Util.C.changeTableColumnOwner());
    table.setText(0, C_ASSIGNEE, Util.C.changeTableColumnAssignee());
    table.setText(0, C_PROJECT, Util.C.changeTableColumnProject());
    table.setText(0, C_BRANCH, Util.C.changeTableColumnBranch());
    table.setText(0, C_LAST_UPDATE, Util.C.changeTableColumnLastUpdate());
    table.setText(0, C_SIZE, Util.C.changeTableColumnSize());

    final FlexCellFormatter fmt = table.getFlexCellFormatter();
    fmt.addStyleName(0, C_STAR, Gerrit.RESOURCES.css().iconHeader());
    for (int i = C_ID; i < columns; i++) {
      fmt.addStyleName(0, i, Gerrit.RESOURCES.css().dataHeader());
    }
    if (!showLegacyId) {
      fmt.addStyleName(0, C_ID, Gerrit.RESOURCES.css().dataHeaderHidden());
    }
    if (!showAssignee) {
      fmt.addStyleName(0, C_ASSIGNEE, Gerrit.RESOURCES.css().dataHeaderHidden());
    }

    table.addClickHandler(
        new ClickHandler() {
          @Override
          public void onClick(final ClickEvent event) {
            final Cell cell = table.getCellForEvent(event);
            if (cell == null) {
              return;
            }
            if (cell.getCellIndex() == C_STAR) {
              // Don't do anything (handled by star itself).
            } else if (cell.getCellIndex() == C_STATUS) {
              // Don't do anything.
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
    return item.legacyId();
  }

  @Override
  protected void onOpenRow(final int row) {
    final ChangeInfo c = getRowItem(row);
    final Change.Id id = c.legacyId();
    Gerrit.display(PageLinks.toChange(c.projectNameKey(), id));
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
    if (!showLegacyId) {
      fmt.addStyleName(row, C_ID, Gerrit.RESOURCES.css().dataCellHidden());
    }
    fmt.addStyleName(row, C_SUBJECT, Gerrit.RESOURCES.css().cSUBJECT());
    fmt.addStyleName(row, C_STATUS, Gerrit.RESOURCES.css().cSTATUS());
    fmt.addStyleName(row, C_OWNER, Gerrit.RESOURCES.css().cOWNER());
    fmt.addStyleName(
        row,
        C_ASSIGNEE,
        showAssignee
            ? Gerrit.RESOURCES.css().cASSIGNEE()
            : Gerrit.RESOURCES.css().dataCellHidden());
    fmt.addStyleName(row, C_LAST_UPDATE, Gerrit.RESOURCES.css().cLastUpdate());
    fmt.addStyleName(row, C_SIZE, Gerrit.RESOURCES.css().cSIZE());

    for (int i = C_SIZE + 1; i < columns; i++) {
      fmt.addStyleName(row, i, Gerrit.RESOURCES.css().cAPPROVAL());
    }
  }

  public void updateColumnsForLabels(ChangeList... lists) {
    labelNames = new ArrayList<>();
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

    int baseColumns = BASE_COLUMNS;
    if (baseColumns + labelNames.size() < columns) {
      int n = columns - (baseColumns + labelNames.size());
      for (int row = 0; row < table.getRowCount(); row++) {
        table.removeCells(row, columns, n);
      }
    }
    columns = baseColumns + labelNames.size();

    FlexCellFormatter fmt = table.getFlexCellFormatter();
    for (int i = 0; i < labelNames.size(); i++) {
      String name = labelNames.get(i);
      int col = baseColumns + i;

      String abbrev = getAbbreviation(name, "-");
      table.setText(0, col, abbrev);
      table.getCellFormatter().getElement(0, col).setTitle(name);
      fmt.addStyleName(0, col, Gerrit.RESOURCES.css().dataHeader());
    }

    for (Section s : sections) {
      if (s.titleRow >= 0) {
        fmt.setColSpan(s.titleRow, 0, columns);
      }
    }
  }

  private void populateChangeRow(final int row, final ChangeInfo c, boolean highlightUnreviewed) {
    CellFormatter fmt = table.getCellFormatter();
    if (Gerrit.isSignedIn()) {
      table.setWidget(row, C_STAR, StarredChanges.createIcon(c.legacyId(), c.starred()));
    }
    table.setWidget(row, C_ID, new TableChangeLink(String.valueOf(c.legacyId()), c));

    String subject = Util.cropSubject(c.subject());
    table.setWidget(row, C_SUBJECT, new TableChangeLink(subject, c));

    Change.Status status = c.status();
    if (status != Change.Status.NEW) {
      table.setText(
          row,
          C_STATUS,
          Util.toLongString(status) + (c.isPrivate() ? (" " + Util.C.isPrivate()) : ""));
    } else if (!c.mergeable()) {
      table.setText(
          row,
          C_STATUS,
          Util.C.changeTableNotMergeable() + (c.isPrivate() ? (" " + Util.C.isPrivate()) : ""));
    } else if (c.isPrivate()) {
      table.setText(row, C_STATUS, Util.C.isPrivate());
    }

    if (c.owner() != null) {
      table.setWidget(row, C_OWNER, AccountLinkPanel.withStatus(c.owner(), status));
    } else {
      table.setText(row, C_OWNER, "");
    }

    if (showAssignee) {
      if (c.assignee() != null) {
        table.setWidget(row, C_ASSIGNEE, AccountLinkPanel.forAssignee(c.assignee()));
        if (Gerrit.getUserPreferences().highlightAssigneeInChangeTable()
            && Objects.equals(c.assignee().getId(), Gerrit.getUserAccount().getId())) {
          table.getRowFormatter().addStyleName(row, Gerrit.RESOURCES.css().cASSIGNEDTOME());
        }
      } else {
        table.setText(row, C_ASSIGNEE, "");
      }
    }

    table.setWidget(row, C_PROJECT, new ProjectLink(c.projectNameKey()));
    table.setWidget(
        row, C_BRANCH, new BranchLink(c.projectNameKey(), c.status(), c.branch(), c.topic()));
    if (Gerrit.getUserPreferences().relativeDateInChangeTable()) {
      table.setText(row, C_LAST_UPDATE, relativeFormat(c.updated()));
    } else {
      table.setText(row, C_LAST_UPDATE, shortFormat(c.updated()));
    }

    int col = C_SIZE;
    if (!Gerrit.getUserPreferences().sizeBarInChangeTable()) {
      table.setText(row, col, Util.M.insertionsAndDeletions(c.insertions(), c.deletions()));
    } else {
      table.setWidget(row, col, getSizeWidget(c));
      fmt.getElement(row, col)
          .setTitle(Util.M.insertionsAndDeletions(c.insertions(), c.deletions()));
    }
    col++;

    for (int idx = 0; idx < labelNames.size(); idx++, col++) {
      String name = labelNames.get(idx);

      LabelInfo label = c.label(name);
      if (label == null) {
        fmt.getElement(row, col).setTitle(Gerrit.C.labelNotApplicable());
        fmt.addStyleName(row, col, Gerrit.RESOURCES.css().labelNotApplicable());
        continue;
      }

      String user;
      String info;
      ReviewCategoryStrategy reviewCategoryStrategy =
          Gerrit.getUserPreferences().reviewCategoryStrategy();
      if (label.rejected() != null) {
        user = label.rejected().name();
        info = getReviewCategoryDisplayInfo(reviewCategoryStrategy, label.rejected());
        if (info != null) {
          FlowPanel panel = new FlowPanel();
          panel.add(new Image(Gerrit.RESOURCES.redNot()));
          panel.add(new InlineLabel(info));
          table.setWidget(row, col, panel);
        } else {
          table.setWidget(row, col, new Image(Gerrit.RESOURCES.redNot()));
        }
      } else if (label.approved() != null) {
        user = label.approved().name();
        info = getReviewCategoryDisplayInfo(reviewCategoryStrategy, label.approved());
        if (info != null) {
          FlowPanel panel = new FlowPanel();
          panel.add(new Image(Gerrit.RESOURCES.greenCheck()));
          panel.add(new InlineLabel(info));
          table.setWidget(row, col, panel);
        } else {
          table.setWidget(row, col, new Image(Gerrit.RESOURCES.greenCheck()));
        }
      } else if (label.disliked() != null) {
        user = label.disliked().name();
        info = getReviewCategoryDisplayInfo(reviewCategoryStrategy, label.disliked());
        String vstr = String.valueOf(label._value());
        if (info != null) {
          vstr = vstr + " " + info;
        }
        fmt.addStyleName(row, col, Gerrit.RESOURCES.css().negscore());
        table.setText(row, col, vstr);
      } else if (label.recommended() != null) {
        user = label.recommended().name();
        info = getReviewCategoryDisplayInfo(reviewCategoryStrategy, label.recommended());
        String vstr = "+" + label._value();
        if (info != null) {
          vstr = vstr + " " + info;
        }
        fmt.addStyleName(row, col, Gerrit.RESOURCES.css().posscore());
        table.setText(row, col, vstr);
      } else {
        table.clearCell(row, col);
        continue;
      }
      fmt.addStyleName(row, col, Gerrit.RESOURCES.css().singleLine());

      if (user != null) {
        // Some web browsers ignore the embedded newline; some like it;
        // so we include a space before the newline to accommodate both.
        fmt.getElement(row, col).setTitle(name + " \nby " + user);
      }
    }

    boolean needHighlight = false;
    if (highlightUnreviewed && !c.reviewed()) {
      needHighlight = true;
    }
    final Element tr = fmt.getElement(row, 0).getParentElement();
    UIObject.setStyleName(tr, Gerrit.RESOURCES.css().needsReview(), needHighlight);

    setRowItem(row, c);
  }

  private static String getReviewCategoryDisplayInfo(
      ReviewCategoryStrategy reviewCategoryStrategy, AccountInfo accountInfo) {
    switch (reviewCategoryStrategy) {
      case NAME:
        return accountInfo.name();
      case EMAIL:
        return accountInfo.email();
      case USERNAME:
        return accountInfo.username();
      case ABBREV:
        return getAbbreviation(accountInfo.name(), " ");
      case NONE:
      default:
        return null;
    }
  }

  private static String getAbbreviation(String name, String token) {
    StringBuilder abbrev = new StringBuilder();
    if (name != null) {
      for (String t : name.split(token)) {
        abbrev.append(t.substring(0, 1).toUpperCase());
      }
    }
    return abbrev.toString();
  }

  private static Widget getSizeWidget(ChangeInfo c) {
    int largeChangeSize = Gerrit.info().change().largeChange();
    int changedLines = c.insertions() + c.deletions();
    int p = 100;
    if (changedLines < largeChangeSize) {
      p = changedLines * 100 / largeChangeSize;
    }

    int width = Math.max(2, 70 * p / 100);
    int red = p >= 50 ? 255 : (int) Math.round((p) * 5.12);
    int green = p <= 50 ? 255 : (int) Math.round(256 - (p - 50) * 5.12);
    String bg = "#" + toHex(red) + toHex(green) + "00";

    SimplePanel panel = new SimplePanel();
    panel.setStyleName(Gerrit.RESOURCES.css().changeSize());
    panel.setWidth(width + "px");
    panel.getElement().getStyle().setBackgroundColor(bg);
    return panel;
  }

  private static String toHex(int i) {
    String hex = Integer.toHexString(i);
    return hex.length() == 1 ? "0" + hex : hex;
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
      super(text, c.legacyId(), c.projectNameKey());
    }

    @Override
    public void go() {
      movePointerTo(cid);
      super.go();
    }
  }

  public static class Section {
    ChangeTable parent;
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
      } else if (titleWidget != null) {
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
        parent.populateChangeRow(dataBegin + i, changeList.get(i), highlightUnreviewed);
      }
    }
  }
}
