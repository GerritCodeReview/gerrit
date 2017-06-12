// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.client.documentation;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.ui.NavigationTable;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;
import com.google.gwt.user.client.ui.HTMLTable.Cell;
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;

class DocTable extends NavigationTable<DocInfo> {
  private static final int C_TITLE = 1;

  private int rows;
  private int dataBeginRow;

  DocTable() {
    super(Util.C.docItemHelp());

    table.setText(0, C_TITLE, Util.C.docTableColumnTitle());

    FlexCellFormatter fmt = table.getFlexCellFormatter();
    fmt.addStyleName(0, C_TITLE, Gerrit.RESOURCES.css().dataHeader());

    table.addClickHandler(
        new ClickHandler() {
          @Override
          public void onClick(ClickEvent event) {
            Cell cell = table.getCellForEvent(event);
            if (cell == null) {
              return;
            }
            if (getRowItem(cell.getRowIndex()) != null) {
              movePointerTo(cell.getRowIndex());
            }
          }
        });
  }

  @Override
  protected Object getRowItemKey(DocInfo item) {
    return item.url();
  }

  @Override
  protected void onOpenRow(int row) {
    DocInfo d = getRowItem(row);
    Window.Location.assign(d.getFullUrl());
  }

  private void insertNoneRow(int row) {
    table.insertRow(row);
    table.setText(row, 0, Util.C.docTableNone());
    FlexCellFormatter fmt = table.getFlexCellFormatter();
    fmt.setStyleName(row, 0, Gerrit.RESOURCES.css().emptySection());
  }

  private void insertDocRow(int row) {
    table.insertRow(row);
    applyDataRowStyle(row);
  }

  @Override
  protected void applyDataRowStyle(int row) {
    super.applyDataRowStyle(row);
    CellFormatter fmt = table.getCellFormatter();
    fmt.addStyleName(row, C_TITLE, Gerrit.RESOURCES.css().dataCell());
    fmt.addStyleName(row, C_TITLE, Gerrit.RESOURCES.css().cSUBJECT());
  }

  private void populateDocRow(int row, DocInfo d) {
    table.setWidget(row, C_TITLE, new DocLink(d));
    setRowItem(row, d);
  }

  public void display(JsArray<DocInfo> docList) {
    int sz = docList != null ? docList.length() : 0;
    boolean hadData = rows > 0;

    if (hadData) {
      while (sz < rows) {
        table.removeRow(dataBeginRow);
        rows--;
      }
    } else {
      table.removeRow(dataBeginRow);
    }

    if (sz == 0) {
      insertNoneRow(dataBeginRow);
      return;
    }

    while (rows < sz) {
      insertDocRow(dataBeginRow + rows);
      rows++;
    }
    for (int i = 0; i < sz; i++) {
      populateDocRow(dataBeginRow + i, docList.get(i));
    }
  }

  public static class DocLink extends Anchor {
    DocLink(DocInfo d) {
      super(com.google.gerrit.client.changes.Util.cropSubject(d.title()));
      setHref(d.getFullUrl());
    }
  }
}
