// Copyright 2008 Google Inc.
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

package com.google.gerrit.client.patches;

import com.google.gerrit.client.data.PatchLine;
import com.google.gerrit.client.ui.FancyFlexTable;
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;

import java.util.List;

public class UnifiedDiffTable extends FancyFlexTable<PatchLine> {
  public UnifiedDiffTable() {
    table.setStyleName("gerrit-UnifiedDiffTable");
  }

  @Override
  protected Object getRowItemKey(final PatchLine item) {
    return null;
  }

  public void display(final List<PatchLine> list) {
    final int sz = list != null ? list.size() : 0;
    int dataRows = table.getRowCount() - 1;
    while (sz < dataRows) {
      table.removeRow(dataRows);
      dataRows--;
    }

    for (int i = 0; i < sz; i++) {
      if (dataRows <= i) {
        table.insertRow(++dataRows);
        applyDataRowStyle(i + 1);
      }
      populate(i + 1, list.get(i));
    }
  }

  @Override
  protected void applyDataRowStyle(final int row) {
    super.applyDataRowStyle(row);
    final CellFormatter fmt = table.getCellFormatter();
    fmt.addStyleName(row, 1, "DiffText");
  }

  private void populate(final int row, final PatchLine line) {
    final CellFormatter fmt = table.getCellFormatter();
    table.setWidget(row, C_ARROW, null);
    table.setText(row, 1, line.getText());
    fmt.setStyleName(row, 1, "DiffText-" + line.getType().name());
    fmt.addStyleName(row, 1, "DiffText");
    setRowItem(row, line);
  }
}
