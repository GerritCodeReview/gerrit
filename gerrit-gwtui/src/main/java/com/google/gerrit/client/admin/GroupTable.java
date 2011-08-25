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

package com.google.gerrit.client.admin;

import com.google.gerrit.client.Dispatcher;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.Hyperlink;
import com.google.gerrit.client.ui.NavigationTable;
import com.google.gerrit.common.data.GroupDetail;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;
import com.google.gwt.user.client.ui.HTMLTable.Cell;
import com.google.gwt.user.client.ui.Image;

import java.util.List;


public class GroupTable extends NavigationTable<AccountGroup> {
  private final boolean enableLink;

  public GroupTable(final boolean enableLink) {
    this(enableLink, null);
  }

  public GroupTable(final boolean enableLink, final String pointerId) {
    this.enableLink = enableLink;

    setSavePointerId(pointerId);
    keysNavigation.add(new PrevKeyCommand(0, 'k', Util.C.groupListPrev()));
    keysNavigation.add(new NextKeyCommand(0, 'j', Util.C.groupListNext()));
    keysNavigation.add(new OpenKeyCommand(0, 'o', Util.C.groupListOpen()));
    keysNavigation.add(new OpenKeyCommand(0, KeyCodes.KEY_ENTER, Util.C
        .groupListOpen()));

    table.setText(0, 1, Util.C.columnGroupName());
    table.setText(0, 2, Util.C.columnGroupDescription());
    table.setText(0, 3, Util.C.headingOwner());
    table.setText(0, 4, Util.C.columnGroupType());
    table.setText(0, 5, Util.C.columnGroupNotifications());
    table.setText(0, 6, Util.C.columnGroupVisibleToAll());
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

    final FlexCellFormatter fmt = table.getFlexCellFormatter();
    fmt.addStyleName(0, 1, Gerrit.RESOURCES.css().dataHeader());
    fmt.addStyleName(0, 2, Gerrit.RESOURCES.css().dataHeader());
    fmt.addStyleName(0, 3, Gerrit.RESOURCES.css().dataHeader());
    fmt.addStyleName(0, 4, Gerrit.RESOURCES.css().dataHeader());
    fmt.addStyleName(0, 5, Gerrit.RESOURCES.css().dataHeader());
    fmt.addStyleName(0, 6, Gerrit.RESOURCES.css().dataHeader());
  }

  @Override
  protected Object getRowItemKey(final AccountGroup item) {
    return item.getId();
  }

  @Override
  protected void onOpenRow(final int row) {
    History.newItem(Dispatcher.toGroup(getRowItem(row).getId()));
  }

  public void display(final List<GroupDetail> result) {
    while (1 < table.getRowCount())
      table.removeRow(table.getRowCount() - 1);

    for(GroupDetail detail : result) {
      final int row = table.getRowCount();
      table.insertRow(row);
      applyDataRowStyle(row);
      populate(row, detail);
    }
  }

  void populate(final int row, final GroupDetail detail) {
    AccountGroup k = detail.group;
    if (enableLink) {
      table.setWidget(row, 1, new Hyperlink(k.getName(),
          Dispatcher.toGroup(k.getId())));
    } else {
      table.setText(row, 1, k.getName());
    }
    table.setText(row, 2, k.getDescription());
    table.setText(row, 3, detail.ownerGroup.getName());
    table.setText(row, 4, k.getType().toString());
    if (k.isEmailOnlyAuthors()) {
      table.setWidget(row, 5, new Image(Gerrit.RESOURCES.greenCheck()));
    }
    if (k.isVisibleToAll()) {
      table.setWidget(row, 6, new Image(Gerrit.RESOURCES.greenCheck()));
    }

    final FlexCellFormatter fmt = table.getFlexCellFormatter();
    fmt.addStyleName(row, 1, Gerrit.RESOURCES.css().dataCell());
    fmt.addStyleName(row, 1, Gerrit.RESOURCES.css().groupName());
    fmt.addStyleName(row, 2, Gerrit.RESOURCES.css().dataCell());
    fmt.addStyleName(row, 3, Gerrit.RESOURCES.css().dataCell());
    fmt.addStyleName(row, 4, Gerrit.RESOURCES.css().dataCell());
    fmt.addStyleName(row, 5, Gerrit.RESOURCES.css().dataCell());
    fmt.addStyleName(row, 6, Gerrit.RESOURCES.css().dataCell());

    setRowItem(row, k);
  }
}
