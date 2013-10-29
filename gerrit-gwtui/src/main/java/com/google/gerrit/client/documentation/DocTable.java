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
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;
import com.google.gwt.user.client.ui.HTMLTable.Cell;
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.List;

public class DocTable extends NavigationTable<DocInfo> {
  private static final int C_TITLE = 1;
  private static final int BASE_COLUMNS = 2;

  private final List<Section> sections;
  private int columns;

  public DocTable() {
    super(Util.C.docItemHelp());
    columns = BASE_COLUMNS;

    sections = new ArrayList<Section>();
    table.setText(0, C_TITLE, Util.C.docTableColumnTitle());

    FlexCellFormatter fmt = table.getFlexCellFormatter();
    fmt.addStyleName(0, C_TITLE, Gerrit.RESOURCES.css().dataHeader());

    table.addClickHandler(new ClickHandler() {
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
    Gerrit.display("/" + d.url());
  }

  private void insertNoneRow(int row) {
    insertRow(row);
    table.setText(row, 0, Util.C.docTableNone());
    FlexCellFormatter fmt = table.getFlexCellFormatter();
    fmt.setColSpan(row, 0, columns);
    fmt.setStyleName(row, 0, Gerrit.RESOURCES.css().emptySection());
  }

  private void insertDocRow(int row) {
    insertRow(row);
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

  public void addSection(Section s) {
    assert s.parent == null;

    s.parent = this;
    s.titleRow = table.getRowCount();
    if (s.displayTitle()) {
      FlexCellFormatter fmt = table.getFlexCellFormatter();
      fmt.setColSpan(s.titleRow, 0, columns);
      fmt.addStyleName(s.titleRow, 0, Gerrit.RESOURCES.css().sectionHeader());
    } else {
      s.titleRow = -1;
    }

    s.dataBegin = table.getRowCount();
    insertNoneRow(s.dataBegin);
    sections.add(s);
  }

  private int insertRow(int beforeRow) {
    for (Section s : sections) {
      if (beforeRow <= s.titleRow) {
        s.titleRow++;
      }
      if (beforeRow < s.dataBegin) {
        s.dataBegin++;
      }
    }
    return table.insertRow(beforeRow);
  }

  private void removeRow(int row) {
    for (Section s : sections) {
      if (row < s.titleRow) {
        s.titleRow--;
      }
      if (row < s.dataBegin) {
        s.dataBegin--;
      }
    }
    table.removeRow(row);
  }

  public class DocLink extends Anchor {
    public DocLink(DocInfo d) {
      super(com.google.gerrit.client.changes.Util.cropSubject(d.title()));
      setHref(GWT.getHostPageBaseURL() + d.url());
    }
  }

  public static class Section {
    DocTable parent;
    String titleText;
    Widget titleWidget;
    int titleRow = -1;
    int dataBegin;
    int rows;

    public void setTitleText(String text) {
      titleText = text;
      titleWidget = null;
      if (titleRow >= 0) {
        parent.table.setText(titleRow, 0, titleText);
      }
    }

    public void setTitleWidget(Widget title) {
      titleWidget = title;
      titleText = null;
      if (titleRow >= 0) {
        parent.table.setWidget(titleRow, 0, title);
      }
    }

    public boolean displayTitle() {
      if (titleText != null) {
        setTitleText(titleText);
        return true;
      } else if(titleWidget != null) {
        setTitleWidget(titleWidget);
        return true;
      }
      return false;
    }

    public void display(JsArray<DocInfo> docList) {
      int sz = docList != null ? docList.length() : 0;
      boolean hadData = rows > 0;

      if (hadData) {
        while (sz < rows) {
          parent.removeRow(dataBegin);
          rows--;
        }
      } else {
        parent.removeRow(dataBegin);
      }

      if (sz == 0) {
        parent.insertNoneRow(dataBegin);
        return;
      }

      while (rows < sz) {
        parent.insertDocRow(dataBegin + rows);
        rows++;
      }
      for (int i = 0; i < sz; i++) {
        parent.populateDocRow(dataBegin + i, docList.get(i));
      }
    }
  }
}
