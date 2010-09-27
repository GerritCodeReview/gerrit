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

import com.google.gerrit.common.data.ProjectData;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.FlexTable;

public class MyFlexTable extends FlexTable {
  private NavigationTable<ProjectData> table;

  public MyFlexTable() {
  }

  public MyFlexTable(final NavigationTable<ProjectData> table) {
    super();
    this.table = table;
  }

  public void onBrowserEvent(final Event event) {
    switch (DOM.eventGetType(event)) {
      case Event.ONCLICK: {
        // Find out which cell was actually clicked.
        final Element td = getEventTargetCell(event);
        if (td == null) {
          break;
        }
        final int row = table.rowOf(td);
        if (table.getRowItem(row) != null) {
          table.movePointerTo(row);
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
        table.onOpenRow(table.rowOf(td));
        return;
      }
    }
    super.onBrowserEvent(event);
  }
}
