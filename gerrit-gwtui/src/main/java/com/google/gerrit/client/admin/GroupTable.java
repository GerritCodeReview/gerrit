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

package com.google.gerrit.client.admin;

import static com.google.gerrit.client.admin.Util.C;

import com.google.gerrit.client.Dispatcher;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.groups.GroupList;
import com.google.gerrit.client.groups.GroupMap;
import com.google.gerrit.client.info.GroupInfo;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.client.ui.HighlightingInlineHyperlink;
import com.google.gerrit.client.ui.NavigationTable;
import com.google.gerrit.client.ui.Util;
import com.google.gerrit.common.PageLinks;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;
import com.google.gwt.user.client.ui.HTMLTable.Cell;
import com.google.gwt.user.client.ui.Image;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class GroupTable extends NavigationTable<GroupInfo> {
  private static final int NUM_COLS = 3;

  public GroupTable() {
    this(null);
  }

  public GroupTable(final String pointerId) {
    super(C.groupItemHelp());
    setSavePointerId(pointerId);

    table.setText(0, 1, C.columnGroupName());
    table.setText(0, 2, C.columnGroupDescription());
    table.setText(0, 3, C.columnGroupVisibleToAll());
    table.addClickHandler(
        new ClickHandler() {
          @Override
          public void onClick(ClickEvent event) {
            final Cell cell = table.getCellForEvent(event);
            if (cell != null
                && cell.getCellIndex() != 1
                && getRowItem(cell.getRowIndex()) != null) {
              movePointerTo(cell.getRowIndex());
            }
          }
        });

    final FlexCellFormatter fmt = table.getFlexCellFormatter();
    for (int i = 1; i <= NUM_COLS; i++) {
      fmt.addStyleName(0, i, Gerrit.RESOURCES.css().dataHeader());
    }
  }

  @Override
  protected Object getRowItemKey(final GroupInfo item) {
    return item.getGroupId();
  }

  @Override
  protected void onOpenRow(final int row) {
    GroupInfo groupInfo = getRowItem(row);
    if (isInteralGroup(groupInfo)) {
      History.newItem(Dispatcher.toGroup(groupInfo.getGroupId()));
    } else if (groupInfo.url() != null) {
      Window.open(groupInfo.url(), "_self", null);
    }
  }

  public void display(GroupMap groups, String toHighlight) {
    display(Natives.asList(groups.values()), toHighlight);
  }

  public void display(GroupList groups) {
    display(Natives.asList(groups), null);
  }

  public void display(List<GroupInfo> list, String toHighlight) {
    displaySubset(list, toHighlight, 0, list.size());
  }

  public void displaySubset(GroupMap groups, int fromIndex, int toIndex, String toHighlight) {
    displaySubset(Natives.asList(groups.values()), toHighlight, fromIndex, toIndex);
  }

  public void displaySubset(List<GroupInfo> list, String toHighlight, int fromIndex, int toIndex) {
    while (1 < table.getRowCount()) {
      table.removeRow(table.getRowCount() - 1);
    }

    Collections.sort(
        list,
        new Comparator<GroupInfo>() {
          @Override
          public int compare(GroupInfo a, GroupInfo b) {
            return a.name().compareTo(b.name());
          }
        });
    for (GroupInfo group : list.subList(fromIndex, toIndex)) {
      final int row = table.getRowCount();
      table.insertRow(row);
      applyDataRowStyle(row);
      populate(row, group, toHighlight);
    }
  }

  void populate(final int row, final GroupInfo k, final String toHighlight) {
    if (k.url() != null) {
      if (isInteralGroup(k)) {
        table.setWidget(
            row,
            1,
            new HighlightingInlineHyperlink(
                k.name(), Dispatcher.toGroup(k.getGroupId()), toHighlight));
      } else {
        Anchor link = new Anchor();
        link.setHTML(Util.highlight(k.name(), toHighlight));
        link.setHref(k.url());
        table.setWidget(row, 1, link);
      }
    } else {
      table.setHTML(row, 1, Util.highlight(k.name(), toHighlight));
    }
    table.setText(row, 2, k.description());
    if (k.options().isVisibleToAll()) {
      table.setWidget(row, 3, new Image(Gerrit.RESOURCES.greenCheck()));
    }

    final FlexCellFormatter fmt = table.getFlexCellFormatter();
    fmt.addStyleName(row, 1, Gerrit.RESOURCES.css().groupName());
    for (int i = 1; i <= NUM_COLS; i++) {
      fmt.addStyleName(row, i, Gerrit.RESOURCES.css().dataCell());
    }

    setRowItem(row, k);
  }

  private boolean isInteralGroup(final GroupInfo groupInfo) {
    return groupInfo != null && groupInfo.url().startsWith("#" + PageLinks.ADMIN_GROUPS);
  }
}
