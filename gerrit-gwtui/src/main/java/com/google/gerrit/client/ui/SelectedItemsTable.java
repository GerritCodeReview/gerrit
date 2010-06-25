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
import com.google.gerrit.client.admin.TableDeleteEvent;
import com.google.gerrit.client.admin.TableDeleteHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.shared.HandlerManager;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.HasVerticalAlignment;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.PushButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** It holds a list of selected items and allow adding or deleting these. */
public class SelectedItemsTable extends FlexTable {
  final private HandlerManager handlerManager = new HandlerManager(this);

  private List<String> insertedRows;

  /**
   * Constructor
   * @param header Table header text.
   */
  public SelectedItemsTable(String header) {
    insertedRows = new ArrayList<String>();

    addStyleName(Gerrit.RESOURCES.css().changeTable());
    setWidth("100%");
    setText(0, 0, header);

    final FlexCellFormatter fmt = getFlexCellFormatter();
    fmt.addStyleName(0, 0, Gerrit.RESOURCES.css().dataHeader());
    fmt.addStyleName(0, 0, Gerrit.RESOURCES.css().dataHeader());
    fmt.addStyleName(0, 1, Gerrit.RESOURCES.css().iconHeader());
  }

  public List<String> getInsertedRows() {
      return insertedRows;
  }

  public void addRow(final String rowText) {
    if (insertedRows.contains(rowText)) {
      return;
    }

    insertedRows.add(rowText);

    if (insertedRows.size() > 1) {
      Collections.sort(insertedRows, new Comparator<String>() {
        public int compare(final String a, final String b) {
          return a.compareTo(b);
        }
      });
    }

    while (1 < getRowCount())
      removeRow(getRowCount() - 1);

    for (final String k : insertedRows) {
      final int row = getRowCount();
      insertRow(row);
      setText(row, 0, k);

      final Image upImage = new Image(Gerrit.RESOURCES.removeSelectedProject());
      final PushButton removeSelecteProjectButton = new PushButton(upImage);

      removeSelecteProjectButton.setStylePrimaryName(Gerrit.RESOURCES.css()
          .buttonTrash());
      removeSelecteProjectButton.addClickHandler(new ClickHandler() {
        @Override
        public void onClick(ClickEvent event) {
          deleteRow(k);

          handlerManager.fireEvent(new TableDeleteEvent(k));
        }
      });

      setWidget(row, 1, removeSelecteProjectButton);

      final FlexCellFormatter fmt = getFlexCellFormatter();
      fmt.addStyleName(row, 0, Gerrit.RESOURCES.css()
          .selectedProjectsDataCell());
      fmt.addStyleName(row, 0, Gerrit.RESOURCES.css().leftMostCell());
      fmt.addStyleName(row, 1, Gerrit.RESOURCES.css()
          .selectedProjectsDataCell());
      fmt.setAlignment(row, 1, HasHorizontalAlignment.ALIGN_CENTER,
          HasVerticalAlignment.ALIGN_MIDDLE);
    }
  }

  public void deleteRow(final String rowText) {
    int removedIndex = insertedRows.indexOf(rowText);
    insertedRows.remove(removedIndex);
    removeRow(removedIndex + 1);
  }

  /**
   * Remove all rows except the header
   */
  public void deleteAllRows() {
    while (getRowCount() > 1) {
      removeRow(1);
    }

    insertedRows.clear();
  }

  public void addTableDeleteHandler(TableDeleteHandler handler) {
    handlerManager.addHandler(TableDeleteEvent.getType(), handler);
  }
 }
