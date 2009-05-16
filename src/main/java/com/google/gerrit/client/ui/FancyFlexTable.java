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

package com.google.gerrit.client.ui;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtexpui.safehtml.client.SafeHtml;

import java.util.Iterator;

public abstract class FancyFlexTable<RowItem> extends Composite {
  private static final FancyFlexTableImpl impl =
      GWT.create(FancyFlexTableImpl.class);

  protected static final String MY_STYLE = "gerrit-ChangeTable";
  protected static final String S_ICON_HEADER = "IconHeader";
  protected static final String S_DATA_HEADER = "DataHeader";
  protected static final String S_ICON_CELL = "IconCell";
  protected static final String S_DATA_CELL = "DataCell";
  protected static final String S_LEFT_MOST_CELL = "LeftMostCell";
  protected static final String S_ACTIVE_ROW = "ActiveRow";

  protected static final int C_ARROW = 0;

  protected final MyFlexTable table;

  protected FancyFlexTable() {
    table = createFlexTable();
    table.addStyleName(MY_STYLE);
    initWidget(table);

    table.setText(0, C_ARROW, "");
    table.getCellFormatter().addStyleName(0, C_ARROW, S_ICON_HEADER);
  }

  protected MyFlexTable createFlexTable() {
    return new MyFlexTable();
  }

  protected RowItem getRowItem(final int row) {
    return FancyFlexTable.<RowItem> getRowItem(table.getCellFormatter()
        .getElement(row, 0));
  }

  protected void setRowItem(final int row, final RowItem item) {
    setRowItem(table.getCellFormatter().getElement(row, 0), item);
  }

  protected void resetHtml(final SafeHtml body) {
    for (final Iterator<Widget> i = table.iterator(); i.hasNext();) {
      i.next();
      i.remove();
    }
    impl.resetHtml(table, body);
  }

  protected void applyDataRowStyle(final int newRow) {
    table.getCellFormatter().addStyleName(newRow, C_ARROW, S_ICON_CELL);
    table.getCellFormatter().addStyleName(newRow, C_ARROW, S_LEFT_MOST_CELL);
  }

  protected static class MyFlexTable extends FlexTable {
  }

  private static final native <ItemType> void setRowItem(Element td, ItemType c)
  /*-{ td['__gerritRowItem'] = c; }-*/;

  private static final native <ItemType> ItemType getRowItem(Element td)
  /*-{ return td['__gerritRowItem']; }-*/;
}
