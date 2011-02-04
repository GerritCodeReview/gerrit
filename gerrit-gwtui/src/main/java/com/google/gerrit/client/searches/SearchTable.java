// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.client.searches;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.ui.Hyperlink;
import com.google.gerrit.client.ui.NavigationTable;
import com.google.gerrit.common.data.SearchList;
import com.google.gerrit.reviewdb.Search;

import com.google.gwt.user.client.History;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;
import com.google.gwt.user.client.ui.HTMLTable.Cell;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SearchTable extends NavigationTable<Search> {
  private static int HIDDEN = -1;

  protected SearchEditor editor;
  private SearchList list;
  private int editorRow = HIDDEN;
  private int columns;
  private boolean inserting = false;

  protected ValueChangeHandler<Boolean> checkVCb =
      new ValueChangeHandler<Boolean>() {
        @Override
        public void onValueChange(ValueChangeEvent<Boolean> event) {
          if (event.getValue()) {
            onSearchChecked(true);
          } else {
            onSearchChecked(!getCheckedSearchKeys().isEmpty());
          }
        }
      };

  public SearchTable() {
    keysNavigation.add(new PrevKeyCommand(0, 'k', Util.C.searchListPrev()));
    keysNavigation.add(new NextKeyCommand(0, 'j', Util.C.searchListNext()));
    keysNavigation.add(new OpenKeyCommand(0, 'o', Util.C.searchListOpen()));
    keysNavigation.add(new OpenKeyCommand(0, KeyCodes.KEY_ENTER, Util.C.searchListOpen()));

    table.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        final Cell cell = table.getCellForEvent(event);
        if (cell != null && cell.getCellIndex() != 1
            && getRowItem(cell.getRowIndex()) != null) {
          movePointerTo(cell.getRowIndex());
        }
      }
    });

    editor = createSearchEditor();
  }

  public void setSearchList(SearchList list, Search.Key key) {
    this.list = list;
    editor.setSearchList(list);
    refresh(key);
  }

  @Override
  public Search.Key getRowItemKey(Search s) {
    return s.getKey();
  }

  @Override
  public void onOpenRow(int row) {
    if (row > 0) {
      movePointerTo(row);
    }
  }

  @Override
  public void movePointerTo(int row) {
    if (editorRow == (row + 1) && ! inserting) {
      editor.onCancel();
    } else {
      if (editorRow != HIDDEN && editorRow < row) {
        row--;
      }
      hideEditor();
      super.movePointerTo(row);
      showEditor(row);
    }
  }

  public void movePointerTo(final Search.Key key) {
    super.movePointerTo(key);
  }

  protected void showEditor(int row) {
    hideEditor();
    if (Gerrit.isSignedIn()) {
      if (row > 0) {
        editor.setSearch(getRowItem(row));
      } else if (row == -1) {
        row = table.getRowCount() - 1;

        Search.Key key = null;
        if (list != null && list.getOwnerInfo() != null) {
          key = new Search.Key(list.getOwnerInfo().getId(), null);
        }
        editor.setSearch(new Search(key, null, null));

        inserting = true;
        clearPointer();
      }
      editorRow = row + 1;
      table.insertRow(editorRow);
      table.setWidget(editorRow, 0, editor);
      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      fmt.setColSpan(editorRow, 0, columns);
      fmt.addStyleName(editorRow, 0, Gerrit.RESOURCES.css().dataHeader());
    }
  }

  protected void hideEditor() {
    if (editorRow > 0) {
      table.removeRow(editorRow);
    }
    editorRow = HIDDEN;
    inserting = false;
  }

  private String link(final Search s) {
    String type = s.getType().getId();
    String owner = list.getOwnerInfo().getOwnerForLink();
    String search = s.getName();
    if (!"".equals(owner)) {
      search = owner + "." + search;
    }
    return "q," + type + "search:" + search + ",n,z";
  }

  public void refresh(Search.Key key) {
    display(list.getSearches());
    if("" != key.get()) {
      movePointerTo(key);
      hideEditor();
    }
  }

  protected void display(final List<Search> searches) {
    hideEditor();

    while (0 < table.getRowCount())
      table.removeRow(table.getRowCount() - 1);

    populateHeader();
    if (searches.isEmpty()) {
      insertNoneRow(1);
    } else {
      for (final Search s : searches) {
        final int row = table.getRowCount();
        table.insertRow(row);
        applyDataRowStyle(row);
        populate(row, s);
      }
    }
  }

  private void insertNoneRow(final int row) {
    table.insertRow(row);
    table.setText(row, 0, Util.C.searchTableNone());
    final FlexCellFormatter fmt = table.getFlexCellFormatter();
    fmt.setColSpan(row, 0, columns);
    fmt.setStyleName(row, 0, Gerrit.RESOURCES.css().emptySection());
  }

  protected void populate(final int row, final Search s) {
    int col = 1;
    if (list.isEditable()) {
      table.setWidget(row, col++, getDeleteBox(row));
    }
    table.setWidget(row, col++, new Hyperlink(s.getName(), link(s)));
    table.setText(row, col++, s.getDescription());
    table.setText(row, col++, s.getQuery());

    final FlexCellFormatter fmt = table.getFlexCellFormatter();
    for (col-- ; col >=0 ; col--)
      fmt.addStyleName(row, col, Gerrit.RESOURCES.css().dataCell());

    col = 1;
    if (list.isEditable()) {
      fmt.addStyleName(row, col++, Gerrit.RESOURCES.css().iconCell());
    }
    fmt.addStyleName(row, col++, Gerrit.RESOURCES.css().cPROJECT());
    fmt.addStyleName(row, col++, Gerrit.RESOURCES.css().cSUBJECT());
    fmt.addStyleName(row, col++, Gerrit.RESOURCES.css().cSUBJECT());

    setRowItem(row, s);
  }

  public Set<Search.Key> getCheckedSearchKeys() {
    final Set<Search.Key> keys = new HashSet<Search.Key>();
    for (int row = 1; row < table.getRowCount(); row++) {
      Search s = getRowItem(row);
      if (s != null && table.getWidget(row, 1) instanceof CheckBox
          && ((CheckBox) table.getWidget(row, 1)).getValue()) {
        keys.add(s.getKey());
      }
    }
    return keys;
  }

  public void onSearchChecked(boolean checked) {
  }

  private Widget getDeleteBox(int row) {
    CheckBox cb = new CheckBox();
    cb.addValueChangeHandler(checkVCb);
    return cb;
  }

  void populateHeader() {
    int row = 0;
    int col = 1;
    if (list.isEditable()) {
      table.setText(row, col++, "");
    }
    table.setText(row, col++, Util.C.columnSearchName());
    table.setText(row, col++, Util.C.columnSearchDescription());
    table.setText(row, col++, Util.C.columnSearchQuery());

    final FlexCellFormatter fmt = table.getFlexCellFormatter();
    col = 0;
    fmt.addStyleName(row, col++, Gerrit.RESOURCES.css().iconHeader());
    if (list.isEditable()) {
      fmt.addStyleName(row, col++, Gerrit.RESOURCES.css().dataHeader());
    }
    fmt.addStyleName(row, col++, Gerrit.RESOURCES.css().dataHeader());
    fmt.addStyleName(row, col++, Gerrit.RESOURCES.css().dataHeader());
    fmt.addStyleName(row, col++, Gerrit.RESOURCES.css().dataHeader());
    columns = col;
  }

  protected SearchEditor createSearchEditor() {
    return new Editor(null);
  }

  protected class Editor extends SearchEditor {
    public Editor(Search s) {
      super(s);
    }

    @Override
    public void onCancel() {
      super.onCancel();
      hideEditor();
    }
  }
}
