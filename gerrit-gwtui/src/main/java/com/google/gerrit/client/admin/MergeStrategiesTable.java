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

package com.google.gerrit.client.admin;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.ui.FancyFlexTable;
import com.google.gerrit.common.data.MergeStrategySection;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.PushButton;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;

import java.util.ArrayList;
import java.util.List;

/**
 * Table to show the merge strategies per ref pattern.
 */
public class MergeStrategiesTable extends
    FancyFlexTable<MergeStrategySection> {
  private String revision;

  public MergeStrategiesTable() {
    table.setWidth("450px");
    table.setText(0, 1, Util.C.columnRefName());
    table.setText(0, 2, Util.C.columnSubmitAction());

    final FlexCellFormatter fmt = table.getFlexCellFormatter();
    fmt.addStyleName(0, 1, Gerrit.RESOURCES.css().dataHeader());
    fmt.addStyleName(0, 2, Gerrit.RESOURCES.css().dataHeader());
    fmt.addStyleName(0, 3, Gerrit.RESOURCES.css().dataHeader());

    fmt.setVisible(0, 3, false);
  }

  /**
   * It adds a new merge strategy (rows).
   */
  public boolean addNew(final MergeStrategySection section) {
    boolean canAdd = true;
    for (int row = 1; row < table.getRowCount(); row++) {
      MergeStrategySection r = getRowItem(row);
      if (r != null && r.getName().equals(section.getName())) {
        // Each pattern can have only one strategy.
        canAdd = false;
        break;
      }
    }

    if (canAdd) {
      final int row = table.getRowCount();
      table.insertRow(row);
      applyDataRowStyle(row);
      populate(row, section);
      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      fmt.setVisible(row, 3, true);
    }

    return canAdd;
  }

  public List<MergeStrategySection> getStrategies() {
    final List<MergeStrategySection> items =
        new ArrayList<MergeStrategySection>();
    for (int row = 1; row < table.getRowCount(); row++) {
      MergeStrategySection r = getRowItem(row);
      if (r != null) {
        items.add(r);
      }
    }

    return items;
  }

  /**
   * Display the merge strategies in the table.
   *
   * @param mergeStrategies The list of merge strategies to display.
   */
  public void display(final List<MergeStrategySection> mergeStrategies) {
    while (1 < table.getRowCount())
      table.removeRow(table.getRowCount() - 1);

    for (final MergeStrategySection r : mergeStrategies) {
      final int row = table.getRowCount();
      table.insertRow(row);
      applyDataRowStyle(row);
      populate(row, r);
    }
  }

  public void setRevision(final String revision) {
    this.revision = revision;
  }

  public String getRevision() {
    return revision;
  }

  /**
   * It populates a row of the table.
   *
   * @param row The row index.
   * @param rms The merge strategy to populate.
   */
  public void populate(final int row, final MergeStrategySection ms) {
    table.setText(row, 1, ms.getName());
    table.setText(row, 2, Util.toLongString(ms.getSubmitType()));

    final Image upImage = new Image(AdminResources.I.deleteHover());
    final PushButton deleteMergeStrategyButton = new PushButton(upImage);

    deleteMergeStrategyButton.setStylePrimaryName(AdminResources.I.css()
        .deleteMergeStrategyButton());
    deleteMergeStrategyButton.addClickHandler(new ClickHandler() {

      @Override
      public void onClick(ClickEvent event) {
        table.removeRow(row);
      }
    });

    table.setWidget(row, 3, deleteMergeStrategyButton);

    final FlexCellFormatter fmt = table.getFlexCellFormatter();
    fmt.addStyleName(row, 1, Gerrit.RESOURCES.css().dataCell());
    fmt.addStyleName(row, 2, Gerrit.RESOURCES.css().dataCell());
    fmt.addStyleName(row, 3, Gerrit.RESOURCES.css().dataCell());

    fmt.setVisible(row, 3, false);

    setRowItem(row, ms);
  }

  public void showDeleteColumn(final boolean show) {
    for (int row = 1; row < table.getRowCount(); row++) {
      MergeStrategySection r = getRowItem(row);
      if (r != null) {
        final FlexCellFormatter fmt = table.getFlexCellFormatter();
        fmt.setVisible(0, 3, show);
        fmt.setVisible(row, 3, show);
      }
    }
  }
}
