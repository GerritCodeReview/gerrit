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

import com.google.gerrit.client.Dispatcher;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.admin.ChangeAllCheckBoxSelectionEvent;
import com.google.gerrit.client.admin.ChangeAllCheckBoxSelectionHandler;
import com.google.gerrit.client.admin.CheckBoxSelectionEvent;
import com.google.gerrit.client.admin.CheckBoxSelectionHandler;
import com.google.gerrit.client.admin.ProjectScreen;
import com.google.gerrit.common.data.ProjectData;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.Project.NameKey;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.HandlerManager;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;

import java.util.List;

public class ProjectsSelectionTable extends ProjectsTable {
  final private HandlerManager handlerManager = new HandlerManager(this);

  private boolean hasSelectAllRow;
  private CheckBox cbSelectAll;

  public ProjectsSelectionTable() {
    super(true);
    keysNavigation.add(new PrevKeyCommand(0, 'k', Util.C.projectListPrev()));
    keysNavigation.add(new NextKeyCommand(0, 'j', Util.C.projectListNext()));
    keysNavigation.add(new OpenKeyCommand(0, 'o', Util.C.projectListOpen()));
    keysNavigation.add(new OpenKeyCommand(0, KeyCodes.KEY_ENTER, Util.C
        .projectListOpen()));

    table.setText(0, 2, Util.C.projectName());
    table.setText(0, 3, Util.C.projectDescription());
    table.setText(0, 4, Util.C.parentProject());

    final FlexCellFormatter fmt = table.getFlexCellFormatter();
    fmt.addStyleName(0, 1, Gerrit.RESOURCES.css().iconHeader());
    fmt.addStyleName(0, 2, Gerrit.RESOURCES.css().dataHeader());
    fmt.addStyleName(0, 3, Gerrit.RESOURCES.css().dataHeader());
    fmt.addStyleName(0, 4, Gerrit.RESOURCES.css().dataHeader());
  }

  @Override
  protected void insert(final int row, final ProjectData k) {
    table.insertRow(row);

    applyDataRowStyle(row);

    final FlexCellFormatter fmt = table.getFlexCellFormatter();
    fmt.addStyleName(row, 1, Gerrit.RESOURCES.css().iconCell());
    fmt.addStyleName(row, 2, Gerrit.RESOURCES.css().dataCell());
    fmt.addStyleName(row, 2, Gerrit.RESOURCES.css().cPROJECT());
    fmt.addStyleName(row, 3, Gerrit.RESOURCES.css().dataCell());
    fmt.addStyleName(row, 4, Gerrit.RESOURCES.css().dataCell());

    populate(row, k);
  }

  @Override
  protected void onOpenRow(final int row) {
    History.newItem(link(getRowItem(row).getParentNameKey()));
  }

  private String link(final NameKey item) {
    return Dispatcher.toProjectAdmin(item, ProjectScreen.INFO);
  }

  public void display(final List<ProjectData> projects) {
    while (1 < table.getRowCount())
      table.removeRow(table.getRowCount() - 1);

    for (final ProjectData k : projects) {
      if (k.isVisible()) {
        insert(table.getRowCount(), k);
      }
    }

    if (projects != null && table.getRowCount() > 1) {
      hasSelectAllRow = true;
      populateSelectAllRow();
    } else {
      hasSelectAllRow = false;
    }

    finishDisplay();
  }

  @Override
  protected void populate(final int row, final ProjectData k) {

    /* Add a checkBox column for each row, except for "All projects" row */
    if (!k.getNameKey().equals(Gerrit.getConfig().getWildProject())) {
      CheckBox cb = new CheckBox();
      cb.addValueChangeHandler(new ValueChangeHandler<Boolean>() {
        @Override
        public void onValueChange(ValueChangeEvent<Boolean> event) {
          handlerManager.fireEvent(new CheckBoxSelectionEvent(event.getValue(),
              k.getName()));
        }
      });
      table.setWidget(row, 1, cb);
    }
    table.setWidget(row, 2, new Hyperlink(k.getName(), link(k.getNameKey())));
    table.setText(row, 3, k.getDescription());

    if (k.getParentNameKey() != null) {
      table.setWidget(row, 4, new Hyperlink(k.getParentNameKey().get(), link(k
          .getParentNameKey())));
    }

    setRowItem(row, k);
  }

  public void populateSelectAllRow() {
    int rowCount = table.getRowCount();
    table.insertRow(rowCount);

    cbSelectAll = new CheckBox();

    cbSelectAll.addValueChangeHandler(new ValueChangeHandler<Boolean>() {
      @Override
      public void onValueChange(ValueChangeEvent<Boolean> event) {
        boolean checked = event.getValue();

        changeAllProjectsSelection(checked, true);

        handlerManager.fireEvent(new ChangeAllCheckBoxSelectionEvent(event
            .getValue()));
      }
    });

    table.setWidget(rowCount, 1, cbSelectAll);
    table.setText(rowCount, 2, Util.C.checkBoxSelectAll());

    final FlexCellFormatter fmt = table.getFlexCellFormatter();
    fmt.setColSpan(rowCount, 2, 2);
    fmt.setStyleName(rowCount, 0, Gerrit.RESOURCES.css().selectAllRow());
    fmt.setStyleName(rowCount, 1, Gerrit.RESOURCES.css().selectAllRow());
    fmt.setStyleName(rowCount, 2, Gerrit.RESOURCES.css().selectAllRow());
    fmt.setStyleName(rowCount, 3, Gerrit.RESOURCES.css().selectAllRow());
  }

  public void setSelectAllValue(boolean check) {
    if (cbSelectAll != null) {
      cbSelectAll.setValue(check);
    }
  }

  public CheckBox getCheckBoxByProject(String projectName) {

    CheckBox cb = null;

    final int projectRowCount =
        hasSelectAllRow ? table.getRowCount() - 1 : table.getRowCount();

    for (int row = 1; row < projectRowCount; row++) {
      ProjectData p = getRowItem(row);
      if ((p != null) && (p.getName().equals(projectName))) {
        Widget widget = table.getWidget(row, 1);
        if (widget instanceof CheckBox) {
          cb = (CheckBox) widget;
        }
        break;
      }
    }

    return cb;
  }

  public void changeAllProjectsSelection(boolean checked,
      boolean updateSelectedList) {
    for (int row = 1; row < table.getRowCount() - 1; row++) {
      if (table.getWidget(row, 1) instanceof CheckBox) {
        ((CheckBox) table.getWidget(row, 1)).setValue(new Boolean(checked),
            updateSelectedList);
      }
    }
  }

  public Project.NameKey getSelectedProjectName() {
    Project.NameKey selectedProject = null;
    for (int row = 1; row < table.getRowCount(); row++) {
      final ProjectData k = getRowItem(row);
      final Widget widget = table.getWidget(row, 1);
      if (k != null && widget instanceof CheckBox
          && ((CheckBox) widget).getValue()) {
        selectedProject = k.getNameKey();
        break;
      }
    }
    return selectedProject;
  }

  public void removeProject(final NameKey project) {
    for (int row = 1; row < table.getRowCount(); row++) {
      final ProjectData k = getRowItem(row);
      if (k != null && k.getNameKey().equals(project)) {
        table.removeRow(row);
        break;
      }
    }
  }

  public void setCheckBoxFocus(int row) {
    final CheckBox checkBox = (CheckBox) table.getWidget(row, 1);
    checkBox.setFocus(true);
  }

  public void addChangeAllCheckBoxSelectionHandler(
      ChangeAllCheckBoxSelectionHandler handler) {
    handlerManager.addHandler(ChangeAllCheckBoxSelectionEvent.getType(),
        handler);
  }

  public void addCheckBoxSelectionHandler(CheckBoxSelectionHandler handler) {
    handlerManager.addHandler(CheckBoxSelectionEvent.getType(), handler);
  }
}
