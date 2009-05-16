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

import com.google.gerrit.client.Link;
import com.google.gerrit.client.reviewdb.AccountGroup;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.AccountScreen;
import com.google.gerrit.client.ui.NavigationTable;
import com.google.gerrit.client.ui.SmallHeading;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Hyperlink;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;
import com.google.gwt.user.client.ui.HTMLTable.Cell;
import com.google.gwtexpui.globalkey.client.NpTextBox;

import java.util.List;

public class GroupListScreen extends AccountScreen {
  private GroupTable groups;

  private NpTextBox addTxt;
  private Button addNew;

  @Override
  public void onLoad() {
    super.onLoad();
    Util.GROUP_SVC
        .ownedGroups(new ScreenLoadCallback<List<AccountGroup>>(this) {
          @Override
          protected void preDisplay(final List<AccountGroup> result) {
            groups.display(result);
            groups.finishDisplay();
          }
        });
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();
    setPageTitle(Util.C.groupListTitle());

    groups = new GroupTable();
    add(groups);

    final VerticalPanel fp = new VerticalPanel();
    fp.setStyleName("gerrit-AddSshKeyPanel");
    fp.add(new SmallHeading(Util.C.headingCreateGroup()));

    addTxt = new NpTextBox();
    addTxt.setVisibleLength(60);
    fp.add(addTxt);

    addNew = new Button(Util.C.buttonCreateGroup());
    addNew.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        doCreateGroup();
      }
    });
    fp.add(addNew);
    add(fp);
  }

  @Override
  public void registerKeys() {
    super.registerKeys();
    groups.setRegisterKeys(true);
  }

  private void doCreateGroup() {
    final String newName = addTxt.getText();
    if (newName == null || newName.length() == 0) {
      return;
    }

    Util.GROUP_SVC.createGroup(newName, new GerritCallback<AccountGroup.Id>() {
      public void onSuccess(final AccountGroup.Id result) {
        History.newItem(Link.toAccountGroup(result));
      }
    });
  }

  private class GroupTable extends NavigationTable<AccountGroup> {
    GroupTable() {
      setSavePointerId(Link.ADMIN_GROUPS);
      keysNavigation.add(new PrevKeyCommand(0, 'k', Util.C.groupListPrev()));
      keysNavigation.add(new NextKeyCommand(0, 'j', Util.C.groupListNext()));
      keysNavigation.add(new OpenKeyCommand(0, 'o', Util.C.groupListOpen()));
      keysNavigation.add(new OpenKeyCommand(0, KeyCodes.KEY_ENTER, Util.C
          .groupListOpen()));

      table.setText(0, 1, Util.C.columnGroupName());
      table.setText(0, 2, Util.C.columnGroupDescription());
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
      fmt.addStyleName(0, 1, S_DATA_HEADER);
      fmt.addStyleName(0, 2, S_DATA_HEADER);
    }

    @Override
    protected Object getRowItemKey(final AccountGroup item) {
      return item.getId();
    }

    @Override
    protected void onOpenItem(final AccountGroup item) {
      History.newItem(Link.toAccountGroup(item.getId()));
    }

    void display(final List<AccountGroup> result) {
      while (1 < table.getRowCount())
        table.removeRow(table.getRowCount() - 1);

      for (final AccountGroup k : result) {
        final int row = table.getRowCount();
        table.insertRow(row);
        applyDataRowStyle(row);
        populate(row, k);
      }
    }

    void populate(final int row, final AccountGroup k) {
      table.setWidget(row, 1, new Hyperlink(k.getName(), Link.toAccountGroup(k
          .getId())));
      table.setText(row, 2, k.getDescription());

      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      fmt.addStyleName(row, 1, S_DATA_CELL);
      fmt.addStyleName(row, 2, S_DATA_CELL);

      setRowItem(row, k);
    }
  }
}
