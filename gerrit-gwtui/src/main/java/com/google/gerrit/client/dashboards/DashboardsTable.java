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

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.client.ui.NavigationTable;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;
import com.google.gwt.user.client.ui.Image;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DashboardsTable extends NavigationTable<DashboardInfo> {
  Project.NameKey project;

  public DashboardsTable(final Project.NameKey project) {
    super(Util.C.dashboardItem());
    this.project = project;
    initColumnHeaders();
  }

  protected void initColumnHeaders() {
    final FlexCellFormatter fmt = table.getFlexCellFormatter();
    fmt.setColSpan(0, 0, 2);
    fmt.addStyleName(0, 1, Gerrit.RESOURCES.css().dataHeader());
    fmt.addStyleName(0, 2, Gerrit.RESOURCES.css().dataHeader());
    fmt.addStyleName(0, 3, Gerrit.RESOURCES.css().dataHeader());
    fmt.addStyleName(0, 4, Gerrit.RESOURCES.css().dataHeader());

    table.setText(0, 1, Util.C.dashboardName());
    table.setText(0, 2, Util.C.dashboardTitle());
    table.setText(0, 3, Util.C.dashboardDescription());
    table.setText(0, 4, Util.C.dashboardInherited());
  }

  public void display(DashboardList dashes) {
    display(Natives.asList(dashes));
  }

  public void display(JsArray<DashboardList> in) {
    Map<String, DashboardInfo> map = new HashMap<String, DashboardInfo>();
    for (DashboardList list : Natives.asList(in)) {
      for (DashboardInfo d : Natives.asList(list)) {
        if (!map.containsKey(d.id())) {
          map.put(d.id(), d);
        }
      }
    }
    display(new ArrayList<DashboardInfo>(map.values()));
  }

  public void display(List<DashboardInfo> list) {
    while (1 < table.getRowCount()) {
      table.removeRow(table.getRowCount() - 1);
    }

    Collections.sort(list, new Comparator<DashboardInfo>() {
      @Override
      public int compare(DashboardInfo a, DashboardInfo b) {
        return a.id().compareTo(b.id());
      }
    });

    String ref = null;
    for(DashboardInfo d : list) {
      if (!d.ref().equals(ref)) {
        ref = d.ref();
        insertTitleRow(table.getRowCount(), ref);
      }
      insert(table.getRowCount(), d);
    }

    finishDisplay();
  }

  protected void insertTitleRow(final int row, String section) {
    table.insertRow(row);

    table.setText(row, 0, section);

    final FlexCellFormatter fmt = table.getFlexCellFormatter();
    fmt.setColSpan(row, 0, 6);
    fmt.addStyleName(row, 0, Gerrit.RESOURCES.css().sectionHeader());
  }

  protected void insert(final int row, final DashboardInfo k) {
    table.insertRow(row);

    applyDataRowStyle(row);

    final FlexCellFormatter fmt = table.getFlexCellFormatter();
    fmt.addStyleName(row, 1, Gerrit.RESOURCES.css().dataCell());
    fmt.addStyleName(row, 2, Gerrit.RESOURCES.css().dataCell());
    fmt.addStyleName(row, 3, Gerrit.RESOURCES.css().dataCell());
    fmt.addStyleName(row, 4, Gerrit.RESOURCES.css().dataCell());
    fmt.addStyleName(row, 5, Gerrit.RESOURCES.css().dataCell());

    populate(row, k);
  }

  protected void populate(final int row, final DashboardInfo k) {
    if (k.isDefault()) {
      table.setWidget(row, 1, new Image(Gerrit.RESOURCES.greenCheck()));
      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      fmt.getElement(row, 1).setTitle(Util.C.dashboardDefaultToolTip());
    }
    table.setWidget(row, 2, new Anchor(k.path(), "#"
            + PageLinks.toProjectDashboard(new Project.NameKey(k.project()), k.id())));
    table.setText(row, 3, k.title() != null ? k.title() : k.path());
    table.setText(row, 4, k.description());
    if (k.definingProject() != null && !k.definingProject().equals(k.project())) {
      table.setWidget(row, 5, new Anchor(k.definingProject(), "#"
          + PageLinks.toProjectDashboards(new Project.NameKey(k.definingProject()))));
    }
    setRowItem(row, k);
  }

  @Override
  protected Object getRowItemKey(final DashboardInfo item) {
    return item.id();
  }

  @Override
  protected void onOpenRow(final int row) {
    if (row > 0) {
      movePointerTo(row);
    }
    History.newItem(getRowItem(row).url());
  }
}
