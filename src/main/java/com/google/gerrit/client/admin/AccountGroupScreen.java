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
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.ClickListener;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.SourcesTableEvents;
import com.google.gwt.user.client.ui.SuggestBox;
import com.google.gwt.user.client.ui.TableListener;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;
import com.google.gwtjsonrpc.client.VoidResult;

import java.util.HashSet;
import java.util.List;

public class AccountGroupScreen extends AccountScreen {
  private AccountGroup.Id groupId;
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
    if (members == null) {
      initUI();
    }

    enableForm(false);
    saveName.setEnabled(false);
    saveOwner.setEnabled(false);
    saveDesc.setEnabled(false);
    super.onLoad();

    Util.GROUP_SVC.groupDetail(groupId,
        new ScreenLoadCallback<AccountGroupDetail>(this) {
          @Override
          protected void prepare(final AccountGroupDetail result) {
            enableForm(true);
            saveName.setEnabled(false);
            saveOwner.setEnabled(false);
            saveDesc.setEnabled(false);
            display(result);
          }
        });
  }

  private void enableForm(final boolean on) {
    ownerTxtBox.setEnabled(on);
    groupNameTxt.setEnabled(on);
    descTxt.setEnabled(on);
    addMemberBox.setEnabled(on);
    delMember.setEnabled(on);
  }

  private void initUI() {
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
    saveName.addClickListener(new ClickListener() {
      public void onClick(Widget sender) {
        final String newName = groupNameTxt.getText().trim();
        Util.GROUP_SVC.renameGroup(groupId, newName,
            new GerritCallback<VoidResult>() {
              public void onSuccess(final VoidResult result) {
                saveName.setEnabled(false);
                setTitleText(Util.M.group(newName));
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
    saveOwner.addClickListener(new ClickListener() {
      public void onClick(Widget sender) {
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
    saveDesc.addClickListener(new ClickListener() {
      public void onClick(Widget sender) {
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
    
    addMemberBox.addClickListener(new ClickListener() {
      public void onClick(final Widget sender) {
        doAddNew();
      }
    });

    members = new MemberTable();

    delMember = new Button(Util.C.buttonDeleteGroupMembers());
    delMember.addClickListener(new ClickListener() {
      public void onClick(final Widget sender) {
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
    setTitleText(Util.M.group(group.getName()));
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
      table.addTableListener(new TableListener() {
        public void onCellClicked(SourcesTableEvents sender, int row, int cell) {
          if (cell != 1 && getRowItem(row) != null) {
            movePointerTo(row);
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
    protected boolean onKeyPress(final char keyCode, final int modifiers) {
      if (super.onKeyPress(keyCode, modifiers)) {
        return true;
      }
      if (modifiers == 0) {
        switch (keyCode) {
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
      cb.setChecked(!cb.isChecked());
    }

    void deleteChecked() {
      final HashSet<AccountGroupMember.Key> ids =
          new HashSet<AccountGroupMember.Key>();
      for (int row = 1; row < table.getRowCount(); row++) {
        final AccountGroupMember k = getRowItem(row);
        if (k != null && ((CheckBox) table.getWidget(row, 1)).isChecked()) {
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
