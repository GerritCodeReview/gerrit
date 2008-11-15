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

package com.google.gerrit.client.changes;

import com.google.gerrit.client.data.ChangeHeader;
import com.google.gwt.user.client.ui.FlexTable;

import java.util.ArrayList;
import java.util.List;

public class ChangeTable extends FlexTable {
  private static final int C_ID = 0;
  private static final int C_SUBJECT = 1;
  private static final int C_OWNER = 2;
  private static final int C_REVIEWERS = 3;
  private static final int C_PROJECT = 4;
  private static final int C_LAST_UPDATE = 5;
  private static final int COLUMNS = 6;

  private final List<Section> sections;

  public ChangeTable() {
    sections = new ArrayList<Section>();
    addStyleName("gerrit-ChangeTable");

    setColumnHeader(C_ID, Util.C.changeTableColumnID());
    setColumnHeader(C_SUBJECT, Util.C.changeTableColumnSubject());
    setColumnHeader(C_OWNER, Util.C.changeTableColumnOwner());
    setColumnHeader(C_REVIEWERS, Util.C.changeTableColumnReviewers());
    setColumnHeader(C_PROJECT, Util.C.changeTableColumnProject());
    setColumnHeader(C_LAST_UPDATE, Util.C.changeTableColumnLastUpdate());

    getFlexCellFormatter().addStyleName(0, C_ID, "gerrit-ChangeTable-ColumnID");
  }

  private void setColumnHeader(final int col, final String text) {
    setText(0, col, text);
    setStyleName(0, col, "gerrit-ChangeTable-ColumnHeader");
  }

  private void insertNoneRow(final int row) {
    insertRow(row);
    setText(row, 0, Util.C.changeTableNone());
    getFlexCellFormatter().setColSpan(row, 0, COLUMNS);
    setStyleName(row, 0, "gerrit-ChangeTable-EmptySectionRow");
  }

  private void insertChangeRow(final int row) {
    insertRow(row);
    setStyleName(row, C_ID, "gerrit-ChangeTable-ColumnID");
  }

  private void populateChangeRow(final int row, final ChangeHeader c) {
    setWidget(row, C_ID, new ChangeLink(String.valueOf(c.id), c));

    String s = c.subject;
    if (c.status != null) {
      s += " (" + c.status + ")";
    }
    setWidget(row, C_SUBJECT, new ChangeLink(s, c));

    setText(row, C_OWNER, c.owner.fullName);
    setText(row, C_REVIEWERS, "TODO");
    setText(row, C_PROJECT, c.project.name);
    setText(row, C_LAST_UPDATE, c.lastUpdate.toString());
  }

  private void setStyleName(final int row, final int col, final String name) {
    getFlexCellFormatter().setStyleName(row, col, name);
  }

  public void addSection(final Section s) {
    assert s.table == null;

    if (s.titleText != null) {
      s.titleRow = getRowCount();
      setText(s.titleRow, 0, s.titleText);
      getFlexCellFormatter().setColSpan(s.titleRow, 0, COLUMNS);
      setStyleName(s.titleRow, 0, "gerrit-ChangeTable-SectionHeader");
    } else {
      s.titleRow = -1;
    }

    s.table = this;
    s.dataBegin = getRowCount();
    insertNoneRow(s.dataBegin);
    sections.add(s);
  }

  @Override
  public int insertRow(final int beforeRow) {
    for (final Section s : sections) {
      boolean dirty = false;
      if (beforeRow <= s.titleRow) {
        s.titleRow++;
      }
      if (beforeRow < s.dataBegin) {
        s.dataBegin++;
      }
    }
    return super.insertRow(beforeRow);
  }

  @Override
  public void removeRow(final int row) {
    for (final Section s : sections) {
      if (row < s.titleRow) {
        s.titleRow--;
      }
      if (row < s.dataBegin) {
        s.dataBegin--;
      }
    }
    super.removeRow(row);
  }

  public static class Section {
    String titleText;

    ChangeTable table;
    int titleRow;
    int dataBegin;
    int rows;

    public Section() {
      this(null);
    }

    public Section(final String titleText) {
      this.titleText = titleText;
    }

    public void display(final List<ChangeHeader> changeList) {
      final int sz = changeList != null ? changeList.size() : 0;
      final boolean hadData = rows > 0;

      if (hadData) {
        while (sz < rows) {
          table.removeRow(dataBegin);
          rows--;
        }
      }

      if (sz == 0) {
        if (hadData) {
          table.insertNoneRow(dataBegin);
        }
      } else {
        if (!hadData) {
          table.removeRow(dataBegin);
        }

        while (rows < sz) {
          table.insertChangeRow(dataBegin + rows);
          rows++;
        }
        for (int i = 0; i < sz; i++) {
          table.populateChangeRow(dataBegin + i, changeList.get(i));
        }
      }
    }
  }
}
