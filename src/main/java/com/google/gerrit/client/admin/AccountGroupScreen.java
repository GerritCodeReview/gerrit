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

import com.google.gerrit.client.data.AccountInfoCache;
import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.AccountGroup;
import com.google.gerrit.client.reviewdb.AccountGroupMember;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.AccountDashboardLink;
import com.google.gerrit.client.ui.AccountGroupSuggestOracle;
import com.google.gerrit.client.ui.AccountScreen;
import com.google.gerrit.client.ui.AddMemberBox;
import com.google.gerrit.client.ui.FancyFlexTable;
import com.google.gerrit.client.ui.SmallHeading;
import com.google.gerrit.client.ui.TextSaveButtonListener;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.SuggestBox;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;
import com.google.gwt.user.client.ui.HTMLTable.Cell;
import com.google.gwtjsonrpc.client.VoidResult;

import java.util.HashSet;
import java.util.List;

public class AccountGroupScreen extends AccountScreen {
  private final AccountGroup.Id groupId;
  private AccountInfoCache accounts = AccountInfoCache.empty();
  private MemberTable members;

  private TextBox groupNameTxt;
  private Button saveName;

  private TextBox ownerTxtBox;
  private SuggestBox ownerTxt;
  private Button saveOwner;

  private TextArea descTxt;
  private Button saveDesc;

  private Panel memberPanel;
  private AddMemberBox addMemberBox;
  private Button delMember;

  public AccountGroupScreen(final AccountGroup.Id toShow) {
    groupId = toShow;
  }

  @Override
  public void onLoad() {
    super.onLoad();
    Util.GROUP_SVC.groupDetail(groupId,
        new ScreenLoadCallback<AccountGroupDetail>(this) {
          @Override
          protected void preDisplay(final AccountGroupDetail result) {
            display(result);
          }
        });
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();
    initName();
    initOwner();
    initDescription();
    initMemberList();
  }

  private void initName() {
    final VerticalPanel groupNamePanel = new VerticalPanel();
    groupNameTxt = new TextBox();
    groupNameTxt.setVisibleLength(60);
    groupNamePanel.add(groupNameTxt);

    saveName = new Button(Util.C.buttonRenameGroup());
    saveName.setEnabled(false);
    saveName.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        final String newName = groupNameTxt.getText().trim();
        Util.GROUP_SVC.renameGroup(groupId, newName,
            new GerritCallback<VoidResult>() {
              public void onSuccess(final VoidResult result) {
                saveName.setEnabled(false);
                setPageTitle(Util.M.group(newName));
              }
            });
      }
    });
    groupNamePanel.add(saveName);
    add(groupNamePanel);

    new TextSaveButtonListener(groupNameTxt, saveName);
  }

  private void initOwner() {
    final VerticalPanel ownerPanel = new VerticalPanel();
    ownerPanel.add(new SmallHeading(Util.C.headingOwner()));

    ownerTxtBox = new TextBox();
    ownerTxtBox.setVisibleLength(60);
    ownerTxt = new SuggestBox(new AccountGroupSuggestOracle(), ownerTxtBox);
    ownerPanel.add(ownerTxt);

    saveOwner = new Button(Util.C.buttonChangeGroupOwner());
    saveOwner.setEnabled(false);
    saveOwner.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        final String newOwner = ownerTxt.getText().trim();
        if (newOwner.length() > 0) {
          Util.GROUP_SVC.changeGroupOwner(groupId, newOwner,
              new GerritCallback<VoidResult>() {
                public void onSuccess(final VoidResult result) {
                  saveOwner.setEnabled(false);
                }
              });
        }
      }
    });
    ownerPanel.add(saveOwner);
    add(ownerPanel);

    new TextSaveButtonListener(ownerTxtBox, saveOwner);
  }

  private void initDescription() {
    final VerticalPanel vp = new VerticalPanel();
    final Label descHdr = new Label();
    vp.add(new SmallHeading(Util.C.headingDescription()));

    descTxt = new TextArea();
    descTxt.setVisibleLines(6);
    descTxt.setCharacterWidth(60);
    vp.add(descTxt);

    saveDesc = new Button(Util.C.buttonSaveDescription());
    saveDesc.setEnabled(false);
    saveDesc.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        final String txt = descTxt.getText().trim();
        Util.GROUP_SVC.changeGroupDescription(groupId, txt,
            new GerritCallback<VoidResult>() {
              public void onSuccess(final VoidResult result) {
                saveDesc.setEnabled(false);
              }
            });
      }
    });
    vp.add(saveDesc);
    add(vp);

    new TextSaveButtonListener(descTxt, saveDesc);
  }

  private void initMemberList() {
    addMemberBox = new AddMemberBox();

    addMemberBox.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        doAddNew();
      }
    });

    members = new MemberTable();

    delMember = new Button(Util.C.buttonDeleteGroupMembers());
    delMember.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        members.deleteChecked();
      }
    });

    memberPanel = new FlowPanel();
    memberPanel.add(new SmallHeading(Util.C.headingMembers()));
    memberPanel.add(addMemberBox);
    memberPanel.add(members);
    memberPanel.add(delMember);
    add(memberPanel);
  }

  private void display(final AccountGroupDetail result) {
    final AccountGroup group = result.group;
    setPageTitle(Util.M.group(group.getName()));
    groupNameTxt.setText(group.getName());
    if (result.ownerGroup != null) {
      ownerTxt.setText(result.ownerGroup.getName());
    } else {
      ownerTxt.setText(Util.M.deletedGroup(group.getOwnerGroupId().get()));
    }
    descTxt.setText(group.getDescription());
    if (result.autoGroup) {
      memberPanel.setVisible(false);
    } else {
      memberPanel.setVisible(true);
      accounts = result.accounts;
      members.display(result.members);
      members.finishDisplay(true);
    }
  }

  void doAddNew() {
    final String nameEmail = addMemberBox.getText();
    if (nameEmail.length() == 0) {
      return;
    }

    addMemberBox.setEnabled(false);
    Util.GROUP_SVC.addGroupMember(groupId, nameEmail,
        new GerritCallback<AccountGroupDetail>() {
          public void onSuccess(final AccountGroupDetail result) {
            addMemberBox.setEnabled(true);
            addMemberBox.setText("");
            if (result.accounts != null && result.members != null) {
              accounts.merge(result.accounts);
              for (final AccountGroupMember m : result.members) {
                members.insertMember(m);
              }
            }
          }

          @Override
          public void onFailure(final Throwable caught) {
            addMemberBox.setEnabled(true);
            super.onFailure(caught);
          }
        });
  }

  private class MemberTable extends FancyFlexTable<AccountGroupMember> {
    MemberTable() {
      table.setText(0, 2, Util.C.columnMember());
      table.setText(0, 3, Util.C.columnEmailAddress());
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
      fmt.addStyleName(0, 1, S_ICON_HEADER);
      fmt.addStyleName(0, 2, S_DATA_HEADER);
      fmt.addStyleName(0, 3, S_DATA_HEADER);
    }

    @Override
    protected Object getRowItemKey(final AccountGroupMember item) {
      return item.getKey();
    }

    @Override
    protected boolean onKeyPress(final KeyPressEvent event) {
      if (super.onKeyPress(event)) {
        return true;
      }
      if (!event.isAnyModifierKeyDown()) {
        switch (event.getCharCode()) {
          case 's':
          case 'c':
            toggleCurrentRow();
            return true;
        }
      }
      return false;
    }

    @Override
    protected void onOpenItem(final AccountGroupMember item) {
      toggleCurrentRow();
    }

    private void toggleCurrentRow() {
      final CheckBox cb = (CheckBox) table.getWidget(getCurrentRow(), 1);
      cb.setValue(!cb.getValue());
    }

    void deleteChecked() {
      final HashSet<AccountGroupMember.Key> ids =
          new HashSet<AccountGroupMember.Key>();
      for (int row = 1; row < table.getRowCount(); row++) {
        final AccountGroupMember k = getRowItem(row);
        if (k != null && ((CheckBox) table.getWidget(row, 1)).getValue()) {
          ids.add(k.getKey());
        }
      }
      if (!ids.isEmpty()) {
        Util.GROUP_SVC.deleteGroupMembers(ids,
            new GerritCallback<VoidResult>() {
              public void onSuccess(final VoidResult result) {
                for (int row = 1; row < table.getRowCount();) {
                  final AccountGroupMember k = getRowItem(row);
                  if (k != null && ids.contains(k.getKey())) {
                    table.removeRow(row);
                  } else {
                    row++;
                  }
                }
              }
            });
      }
    }

    void insertMember(final AccountGroupMember k) {
      final int row = table.getRowCount();
      table.insertRow(row);
      applyDataRowStyle(row);
      populate(row, k);
    }

    void display(final List<AccountGroupMember> result) {
      while (1 < table.getRowCount())
        table.removeRow(table.getRowCount() - 1);

      for (final AccountGroupMember k : result) {
        final int row = table.getRowCount();
        table.insertRow(row);
        applyDataRowStyle(row);
        populate(row, k);
      }
    }

    void populate(final int row, final AccountGroupMember k) {
      final Account.Id accountId = k.getAccountId();
      table.setWidget(row, 1, new CheckBox());
      table.setWidget(row, 2, AccountDashboardLink.link(accounts, accountId));
      table.setText(row, 3, accounts.get(accountId).getPreferredEmail());

      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      fmt.addStyleName(row, 1, S_ICON_CELL);
      fmt.addStyleName(row, 2, S_DATA_CELL);
      fmt.addStyleName(row, 3, S_DATA_CELL);

      setRowItem(row, k);
    }
  }
}
