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
import com.google.gerrit.client.ui.AccountDashboardLink;
import com.google.gerrit.client.ui.AddIncludedGroupBox;
import com.google.gerrit.client.ui.AddMemberBox;
import com.google.gerrit.client.ui.FancyFlexTable;
import com.google.gerrit.client.ui.Hyperlink;
import com.google.gerrit.client.ui.SmallHeading;
import com.google.gerrit.common.data.AccountInfoCache;
import com.google.gerrit.common.data.GroupDetail;
import com.google.gerrit.common.data.GroupInfo;
import com.google.gerrit.common.data.GroupInfoCache;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.AccountGroupInclude;
import com.google.gerrit.reviewdb.AccountGroupMember;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwtjsonrpc.client.VoidResult;

import java.util.HashSet;
import java.util.List;

public class AccountGroupMembersScreen extends AccountGroupScreen {

  private AccountInfoCache accounts = AccountInfoCache.empty();
  private GroupInfoCache groups = GroupInfoCache.empty();
  private MemberTable members;
  private IncludeTable includes;

  private Panel memberPanel;
  private AddMemberBox addMemberBox;
  private Button delMember;

  private Panel includePanel;
  private AddIncludedGroupBox addIncludeBox;
  private Button delInclude;

  private FlowPanel noMembersInfo;

  public AccountGroupMembersScreen(final GroupDetail toShow, final String token) {
    super(toShow, token);
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();
    initMemberList();
    initIncludeList();
    initNoMembersInfo();
  }

  private void enableForm(final boolean canModify) {
    addMemberBox.setEnabled(canModify);
    members.setEnabled(canModify);
    addIncludeBox.setEnabled(canModify);
    includes.setEnabled(canModify);
  }


  private void initMemberList() {
    addMemberBox = new AddMemberBox();

    addMemberBox.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        doAddNewMember();
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

  private void initIncludeList() {
    addIncludeBox = new AddIncludedGroupBox();

    addIncludeBox.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        doAddNewInclude();
      }
    });

    includes = new IncludeTable();

    delInclude = new Button(Util.C.buttonDeleteIncludedGroup());
    delInclude.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        includes.deleteChecked();
      }
    });

    includePanel = new FlowPanel();
    includePanel.add(new SmallHeading(Util.C.headingIncludedGroups()));
    includePanel.add(addIncludeBox);
    includePanel.add(includes);
    includePanel.add(delInclude);
    add(includePanel);
  }

  private void initNoMembersInfo() {
    noMembersInfo = new FlowPanel();
    noMembersInfo.setVisible(false);
    noMembersInfo.add(new SmallHeading(Util.C.noMembersInfo()));
    add(noMembersInfo);
  }

  @Override
  protected void display(final GroupDetail groupDetail) {
    switch (groupDetail.group.getType()) {
      case INTERNAL:
        accounts = groupDetail.accounts;
        groups = groupDetail.groups;
        members.display(groupDetail.members);
        includes.display(groupDetail.includes);
        break;
      default:
        memberPanel.setVisible(false);
        includePanel.setVisible(false);
        noMembersInfo.setVisible(true);
        break;
    }

    enableForm(groupDetail.canModify);
    delMember.setVisible(groupDetail.canModify);
    delInclude.setVisible(groupDetail.canModify);
  }

  void doAddNewMember() {
    final String nameEmail = addMemberBox.getText();
    if (nameEmail.length() == 0) {
      return;
    }

    addMemberBox.setEnabled(false);
    Util.GROUP_SVC.addGroupMember(getGroupId(), nameEmail,
        new GerritCallback<GroupDetail>() {
          public void onSuccess(final GroupDetail result) {
            addMemberBox.setEnabled(true);
            addMemberBox.setText("");
            if (result.accounts != null && result.members != null) {
              accounts.merge(result.accounts);
              members.display(result.members);
            }
          }

          @Override
          public void onFailure(final Throwable caught) {
            addMemberBox.setEnabled(true);
            super.onFailure(caught);
          }
        });
  }

  void doAddNewInclude() {
    final String groupName = addIncludeBox.getText();
    if (groupName.length() == 0) {
      return;
    }

    addIncludeBox.setEnabled(false);
    Util.GROUP_SVC.addGroupInclude(getGroupId(), groupName,
        new GerritCallback<GroupDetail>() {
          public void onSuccess(final GroupDetail result) {
            addIncludeBox.setEnabled(true);
            addIncludeBox.setText("");
            if (result.groups != null && result.includes != null) {
              groups.merge(result.groups);
              includes.display(result.includes);
            }
          }

          @Override
          public void onFailure(final Throwable caught) {
            addIncludeBox.setEnabled(true);
            super.onFailure(caught);
          }
        });
  }

  private class MemberTable extends FancyFlexTable<AccountGroupMember> {
    private boolean enabled = true;

    MemberTable() {
      table.setText(0, 2, Util.C.columnMember());
      table.setText(0, 3, Util.C.columnEmailAddress());

      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      fmt.addStyleName(0, 1, Gerrit.RESOURCES.css().iconHeader());
      fmt.addStyleName(0, 2, Gerrit.RESOURCES.css().dataHeader());
      fmt.addStyleName(0, 3, Gerrit.RESOURCES.css().dataHeader());
    }

    void setEnabled(final boolean enabled) {
      this.enabled = enabled;
      for (int row = 1; row < table.getRowCount(); row++) {
        final AccountGroupMember k = getRowItem(row);
        if (k != null) {
          ((CheckBox) table.getWidget(row, 1)).setEnabled(enabled);
        }
      }
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
        Util.GROUP_SVC.deleteGroupMembers(getGroupId(), ids,
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
      CheckBox checkBox = new CheckBox();
      table.setWidget(row, 1, checkBox);
      checkBox.setEnabled(enabled);
      table.setWidget(row, 2, AccountDashboardLink.link(accounts, accountId));
      table.setText(row, 3, accounts.get(accountId).getPreferredEmail());

      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      fmt.addStyleName(row, 1, Gerrit.RESOURCES.css().iconCell());
      fmt.addStyleName(row, 2, Gerrit.RESOURCES.css().dataCell());
      fmt.addStyleName(row, 3, Gerrit.RESOURCES.css().dataCell());

      setRowItem(row, k);
    }
  }

  private class IncludeTable extends FancyFlexTable<AccountGroupInclude> {
    private boolean enabled = true;

    IncludeTable() {
      table.setText(0, 2, Util.C.columnGroupName());
      table.setText(0, 3, Util.C.columnGroupDescription());

      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      fmt.addStyleName(0, 1, Gerrit.RESOURCES.css().iconHeader());
      fmt.addStyleName(0, 2, Gerrit.RESOURCES.css().dataHeader());
      fmt.addStyleName(0, 3, Gerrit.RESOURCES.css().dataHeader());
    }

    void setEnabled(final boolean enabled) {
      this.enabled = enabled;
      for (int row = 1; row < table.getRowCount(); row++) {
        final AccountGroupInclude k = getRowItem(row);
        if (k != null) {
          ((CheckBox) table.getWidget(row, 1)).setEnabled(enabled);
        }
      }
    }

    void deleteChecked() {
      final HashSet<AccountGroupInclude.Key> keys =
          new HashSet<AccountGroupInclude.Key>();
      for (int row = 1; row < table.getRowCount(); row++) {
        final AccountGroupInclude k = getRowItem(row);
        if (k != null && ((CheckBox) table.getWidget(row, 1)).getValue()) {
          keys.add(k.getKey());
        }
      }
      if (!keys.isEmpty()) {
        Util.GROUP_SVC.deleteGroupIncludes(getGroupId(), keys,
            new GerritCallback<VoidResult>() {
              public void onSuccess(final VoidResult result) {
                for (int row = 1; row < table.getRowCount();) {
                  final AccountGroupInclude k = getRowItem(row);
                  if (k != null && keys.contains(k.getKey())) {
                    table.removeRow(row);
                  } else {
                    row++;
                  }
                }
              }
            });
      }
    }

    void display(final List<AccountGroupInclude> result) {
      while (1 < table.getRowCount())
        table.removeRow(table.getRowCount() - 1);

      for (final AccountGroupInclude k : result) {
        final int row = table.getRowCount();
        table.insertRow(row);
        applyDataRowStyle(row);
        populate(row, k);
      }
    }

    void populate(final int row, final AccountGroupInclude k) {
      AccountGroup.Id id = k.getIncludeId();
      GroupInfo group = groups.get(id);
      CheckBox checkBox = new CheckBox();
      table.setWidget(row, 1, checkBox);
      checkBox.setEnabled(enabled);
      table.setWidget(row, 2,
          new Hyperlink(group.getName(), Dispatcher.toAccountGroup(id)));
      table.setText(row, 3, groups.get(id).getDescription());

      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      fmt.addStyleName(row, 1, Gerrit.RESOURCES.css().iconCell());
      fmt.addStyleName(row, 2, Gerrit.RESOURCES.css().dataCell());
      fmt.addStyleName(row, 3, Gerrit.RESOURCES.css().dataCell());

      setRowItem(row, k);
    }
  }
}
