// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.client.dashboards;

import com.google.gerrit.client.ui.NavigationTable;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class DashboardsTable extends NavigationTable<DashboardInfo> {
  public DashboardsTable() {
    super(Util.C.dashboardItem());
    initColumnHeaders();
  }

  protected void initColumnHeaders() {
    table.setText(0, 1, Util.C.dashboardName());
    table.setText(0, 2, Util.C.dashboardDescription());

    final FlexCellFormatter fmt = table.getFlexCellFormatter();
    fmt.addStyleName(0, 1, Gerrit.RESOURCES.css().dataHeader());
    fmt.addStyleName(0, 2, Gerrit.RESOURCES.css().dataHeader());
  }

  public void display(DashboardMap dashes) {
    while (1 < table.getRowCount()) {
      table.removeRow(table.getRowCount() - 1);
    }

    List<DashboardInfo> list = dashes.values().asList();
    Collections.sort(list, new Comparator<DashboardInfo>() {
      @Override
      public int compare(DashboardInfo a, DashboardInfo b) {
        return a.id().compareTo(b.id());
      }
    });

    String section = null;
    for(DashboardInfo d : list) {
      if (!d.refName().equals(section)) {
        section = d.refName();
        insertTitleRow(table.getRowCount(), section);
      }
      insert(table.getRowCount(), d);
    }

    finishDisplay();
  }

  protected void insertTitleRow(final int row, String section) {
    table.insertRow(row);

    table.setText(row, 0, section);

    final FlexCellFormatter fmt = table.getFlexCellFormatter();
    fmt.setColSpan(row, 0, 3);
    fmt.addStyleName(row, 0, Gerrit.RESOURCES.css().sectionHeader());
  }

  protected void insert(final int row, final DashboardInfo k) {
    table.insertRow(row);

    applyDataRowStyle(row);

    final FlexCellFormatter fmt = table.getFlexCellFormatter();
    fmt.addStyleName(row, 1, Gerrit.RESOURCES.css().dataCell());
    fmt.addStyleName(row, 2, Gerrit.RESOURCES.css().dataCell());

    populate(row, k);
  }

  protected void populate(final int row, final DashboardInfo k) {
    table.setWidget(row, 1, new Anchor(k.name(), "#" + link(k)));
    table.setText(row, 2, k.description());

    setRowItem(row, k);
  }

  @Override
  protected Object getRowItemKey(final DashboardInfo item) {
    return item.name();
  }

  @Override
  protected void onOpenRow(final int row) {
    if (row > 0) {
      movePointerTo(row);
    }
    History.newItem(link(getRowItem(row)));
  }

  private String link(final DashboardInfo item) {
    return "/dashboard/?" + item.parameters();
  }
}
