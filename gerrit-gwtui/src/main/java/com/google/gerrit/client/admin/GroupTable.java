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

import com.google.gerrit.client.Dispatcher;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.groups.GroupInfo;
import com.google.gerrit.client.groups.GroupMap;
import com.google.gerrit.client.ui.HighlightingInlineHyperlink;
import com.google.gerrit.client.ui.NavigationTable;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;
import com.google.gwt.user.client.ui.HTMLTable.Cell;
import com.google.gwt.user.client.ui.Image;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;


public class GroupTable extends NavigationTable<GroupInfo> {
  private static final int NUM_COLS = 3;

  private final boolean enableLink;

  public GroupTable(final boolean enableLink) {
    this(enableLink, null);
  }

  public GroupTable(final boolean enableLink, final String pointerId) {
    super(Util.C.groupItemHelp());
    this.enableLink = enableLink;
    setSavePointerId(pointerId);

    table.setText(0, 1, Util.C.columnGroupName());
    table.setText(0, 2, Util.C.columnGroupDescription());
    table.setText(0, 3, Util.C.columnGroupVisibleToAll());
    table.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        final Cell cell = table.getCellForEvent(event);
        if (cell != null && cell.getCellIndex() != 1
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
    History.newItem(Dispatcher.toGroup(getRowItem(row).getGroupId()));
  }

  public void display(final GroupMap groups) {
    display(groups, null);
  }

  public void display(final GroupMap groups, final String toHighlight) {
    while (1 < table.getRowCount())
      table.removeRow(table.getRowCount() - 1);

    List<GroupInfo> list = groups.values().asList();
    Collections.sort(list, new Comparator<GroupInfo>() {
      @Override
      public int compare(GroupInfo a, GroupInfo b) {
        return a.name().compareTo(b.name());
      }
    });
    for(GroupInfo group : list) {
      final int row = table.getRowCount();
      table.insertRow(row);
      applyDataRowStyle(row);
      populate(row, group, toHighlight);
    }
  }

  void populate(final int row, final GroupInfo k, final String toHighlight) {
    if (enableLink) {
      table.setWidget(row, 1, new HighlightingInlineHyperlink(k.name(),
          Dispatcher.toGroup(k.getGroupId()), toHighlight));
    } else {
      table.setText(row, 1, k.name());
    }
    table.setText(row, 2, k.description());
    if (k.isVisibleToAll()) {
      table.setWidget(row, 3, new Image(Gerrit.RESOURCES.greenCheck()));
    }

    final FlexCellFormatter fmt = table.getFlexCellFormatter();
    fmt.addStyleName(row, 1, Gerrit.RESOURCES.css().groupName());
    for (int i = 1; i <= NUM_COLS; i++) {
      fmt.addStyleName(row, i, Gerrit.RESOURCES.css().dataCell());
    }

    setRowItem(row, k);
  }
}
