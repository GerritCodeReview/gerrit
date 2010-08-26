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

package com.google.gerrit.client.account;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.ui.NavigationTable;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.reviewdb.Project;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.logical.shared.HasValueChangeHandlers;
import com.google.gwt.event.shared.HandlerRegistration;

import java.util.List;

public class MyUnwatchedProjectsTable extends NavigationTable<Project>
    implements HasValueChangeHandlers<Project> {


  public MyUnwatchedProjectsTable() {
    setSavePointerId(PageLinks.SETTINGS_PROJECTS);
    keysNavigation.add(new PrevKeyCommand(0, 'k',
      Util.C.unwatchedProjectListPrev()));
    keysNavigation.add(new NextKeyCommand(0, 'j',
      Util.C.unwatchedProjectListNext()));
    keysNavigation.add(new OpenKeyCommand(0, KeyCodes.KEY_ENTER,
      Util.C.unwatchedProjectListOpen()));

    table.setText(0, 1, Util.C.unwatchedProjectName());
    table.setText(0, 2, Util.C.unwatchedProjectDescription());

    final FlexCellFormatter fmt = table.getFlexCellFormatter();
    fmt.addStyleName(0, 1, Gerrit.RESOURCES.css().dataHeader());
    fmt.addStyleName(0, 2, Gerrit.RESOURCES.css().dataHeader());

    table.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        onOpenRow(table.getCellForEvent(event).getRowIndex());
      }
    });
  }

  @Override
  protected Object getRowItemKey(final Project item) {
    return item.getNameKey();
  }

  @Override
  protected void onOpenRow(final int row) {
    if (row > 0) {
      movePointerTo(row);
      ValueChangeEvent.fire(MyUnwatchedProjectsTable.this, getRowItem(row));
    }
  }

  void display(final List<Project> projects) {
    while (1 < table.getRowCount())
      table.removeRow(table.getRowCount() - 1);

    for (final Project k : projects)
      insert(table.getRowCount(), k);

    finishDisplay();
  }

  void insert(final int row, final Project k) {
    table.insertRow(row);

    applyDataRowStyle(row);

    final FlexCellFormatter fmt = table.getFlexCellFormatter();
    fmt.addStyleName(row, 1, Gerrit.RESOURCES.css().dataCell());
    fmt.addStyleName(row, 1, Gerrit.RESOURCES.css().cPROJECT());
    fmt.addStyleName(row, 2, Gerrit.RESOURCES.css().dataCell());

    populate(row, k);
  }

  void populate(final int row, final Project k) {
    table.setText(row, 1, k.getName());
    table.setText(row, 2, k.getDescription());

    setRowItem(row, k);
  }

  public HandlerRegistration addValueChangeHandler(
      final ValueChangeHandler<Project> handler) {
    return addHandler(handler, ValueChangeEvent.getType());
  }
}
