// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.client.ui;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.projects.ProjectInfo;
import com.google.gerrit.client.projects.ProjectMap;
import com.google.gerrit.client.rpc.Natives;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;
import com.google.gwt.user.client.ui.Image;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ProjectsTable extends NavigationTable<ProjectInfo> {
  public static final int C_STATE = 1;
  public static final int C_NAME = 2;
  public static final int C_DESCRIPTION = 3;
  public static final int C_REPO_BROWSER = 4;

  public ProjectsTable() {
    super(Util.C.projectItemHelp());
    initColumnHeaders();
  }

  protected void initColumnHeaders() {
    table.setText(0, C_STATE, Util.C.projectStateAbbrev());
    table.getCellFormatter().getElement(0, C_STATE).setTitle(Util.C.projectStateHelp());
    table.setText(0, C_NAME, Util.C.projectName());
    table.setText(0, C_DESCRIPTION, Util.C.projectDescription());

    final FlexCellFormatter fmt = table.getFlexCellFormatter();
    fmt.addStyleName(0, C_STATE, Gerrit.RESOURCES.css().iconHeader());
    fmt.addStyleName(0, C_NAME, Gerrit.RESOURCES.css().dataHeader());
    fmt.addStyleName(0, C_DESCRIPTION, Gerrit.RESOURCES.css().dataHeader());
  }

  @Override
  protected Object getRowItemKey(final ProjectInfo item) {
    return item.name();
  }

  @Override
  protected void onOpenRow(final int row) {
    if (row > 0) {
      movePointerTo(row);
    }
  }

  public void display(ProjectMap projects) {
    displaySubset(projects, 0, projects.size());
  }

  public void displaySubset(ProjectMap projects, int fromIndex, int toIndex) {
    while (1 < table.getRowCount()) {
      table.removeRow(table.getRowCount() - 1);
    }

    List<ProjectInfo> list = Natives.asList(projects.values());
    Collections.sort(
        list,
        new Comparator<ProjectInfo>() {
          @Override
          public int compare(ProjectInfo a, ProjectInfo b) {
            return a.name().compareTo(b.name());
          }
        });
    for (ProjectInfo p : list.subList(fromIndex, toIndex)) {
      insert(table.getRowCount(), p);
    }

    finishDisplay();
  }

  protected void insert(final int row, final ProjectInfo k) {
    table.insertRow(row);

    applyDataRowStyle(row);

    final FlexCellFormatter fmt = table.getFlexCellFormatter();
    fmt.addStyleName(row, C_STATE, Gerrit.RESOURCES.css().iconCell());
    fmt.addStyleName(row, C_NAME, Gerrit.RESOURCES.css().dataCell());
    fmt.addStyleName(row, C_NAME, Gerrit.RESOURCES.css().projectNameColumn());
    fmt.addStyleName(row, C_DESCRIPTION, Gerrit.RESOURCES.css().dataCell());

    populate(row, k);
  }

  protected void populate(final int row, final ProjectInfo k) {
    populateState(row, k);
    table.setText(row, C_NAME, k.name());
    table.setText(row, C_DESCRIPTION, k.description());

    setRowItem(row, k);
  }

  protected void populateState(int row, ProjectInfo k) {
    Image state = new Image();
    switch (k.state()) {
      case HIDDEN:
        state.setResource(Gerrit.RESOURCES.redNot());
        state.setTitle(com.google.gerrit.client.admin.Util.toLongString(k.state()));
        table.setWidget(row, ProjectsTable.C_STATE, state);
        break;
      case READ_ONLY:
        state.setResource(Gerrit.RESOURCES.readOnly());
        state.setTitle(com.google.gerrit.client.admin.Util.toLongString(k.state()));
        table.setWidget(row, ProjectsTable.C_STATE, state);
        break;
      case ACTIVE:
      default:
        // Intentionally left blank, do not show an icon when active.
        break;
    }
  }
}
