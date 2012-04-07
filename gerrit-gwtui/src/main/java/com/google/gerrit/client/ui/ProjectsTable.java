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
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ProjectsTable extends NavigationTable<ProjectInfo> {

  public ProjectsTable() {
    keysNavigation.add(new PrevKeyCommand(0, 'k', Util.C.projectListPrev()));
    keysNavigation.add(new NextKeyCommand(0, 'j', Util.C.projectListNext()));
    keysNavigation.add(new OpenKeyCommand(0, 'o', Util.C.projectListOpen()));
    keysNavigation.add(new OpenKeyCommand(0, KeyCodes.KEY_ENTER,
                                                  Util.C.projectListOpen()));

    table.setText(0, 1, Util.C.projectName());
    table.setText(0, 2, Util.C.projectDescription());

    final FlexCellFormatter fmt = table.getFlexCellFormatter();
    fmt.addStyleName(0, 1, Gerrit.RESOURCES.css().dataHeader());
    fmt.addStyleName(0, 2, Gerrit.RESOURCES.css().dataHeader());
  }

  @Override
  protected MyFlexTable createFlexTable() {
    MyFlexTable table = new MyFlexTable() {
      @Override
      public void onBrowserEvent(final Event event) {
        switch (DOM.eventGetType(event)) {
          case Event.ONCLICK: {
            // Find out which cell was actually clicked.
            final Element td = getEventTargetCell(event);
            if (td == null) {
              break;
            }
            final int row = rowOf(td);
            if (getRowItem(row) != null) {
              ProjectsTable.this.movePointerTo(row);
              return;
            }
            break;
          }
          case Event.ONDBLCLICK: {
            // Find out which cell was actually clicked.
            Element td = getEventTargetCell(event);
            if (td == null) {
              return;
            }
            onOpenRow(rowOf(td));
            return;
          }
        }
        super.onBrowserEvent(event);
      }
    };

    table.sinkEvents(Event.ONDBLCLICK | Event.ONCLICK);
    return table;
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
    while (1 < table.getRowCount())
      table.removeRow(table.getRowCount() - 1);

    List<ProjectInfo> list = projects.values().asList();
    Collections.sort(list, new Comparator<ProjectInfo>() {
      @Override
      public int compare(ProjectInfo a, ProjectInfo b) {
        return a.name().compareTo(b.name());
      }
    });
    for(ProjectInfo p : list)
      insert(table.getRowCount(), p);

    finishDisplay();
  }

  protected void insert(final int row, final ProjectInfo k) {
    table.insertRow(row);

    applyDataRowStyle(row);

    final FlexCellFormatter fmt = table.getFlexCellFormatter();
    fmt.addStyleName(row, 1, Gerrit.RESOURCES.css().dataCell());
    fmt.addStyleName(row, 1, Gerrit.RESOURCES.css().cPROJECT());
    fmt.addStyleName(row, 2, Gerrit.RESOURCES.css().dataCell());

    populate(row, k);
  }

  protected void populate(final int row, final ProjectInfo k) {
    table.setText(row, 1, k.name());
    table.setText(row, 2, k.description());

    setRowItem(row, k);
  }
}
