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

package com.google.gerrit.client.admin;

import com.google.gerrit.client.data.AccountInfoCache;
import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.AccountGroup;
import com.google.gerrit.client.reviewdb.AccountGroupMember;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.AccountDashboardLink;
import com.google.gerrit.client.ui.AccountSuggestOracle;
import com.google.gerrit.client.ui.FancyFlexTable;
import com.google.gerrit.client.ui.Screen;
import com.google.gerrit.client.ui.TextSaveButtonListener;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.ClickListener;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.FocusListenerAdapter;
import com.google.gwt.user.client.ui.Label;
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

public class AccountGroupScreen extends Screen {
  private AccountGroup.Id groupId;
  private AccountInfoCache accounts = AccountInfoCache.empty();
  private MemberTable members;

  private TextArea descTxt;
  private Button saveDesc;
  private Button addMember;
  private TextBox nameTxtBox;
  private SuggestBox nameTxt;
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
    saveDesc.setEnabled(false);
    super.onLoad();

    Util.GROUP_SVC.groupDetail(groupId,
        new GerritCallback<AccountGroupDetail>() {
          public void onSuccess(final AccountGroupDetail result) {
            enableForm(true);
            saveDesc.setEnabled(false);
            display(result);
          }
        });
  }

  private void enableForm(final boolean on) {
    descTxt.setEnabled(on);
    addMember.setEnabled(on);
    nameTxtBox.setEnabled(on);
    delMember.setEnabled(on);
  }

  private void initUI() {
    {
      final VerticalPanel vp = new VerticalPanel();
      final Label descHdr = new Label(Util.C.headingDescription());
      descHdr.setStyleName("gerrit-SmallHeading");
      vp.add(descHdr);

      descTxt = new TextArea();
      descTxt.setVisibleLines(6);
      descTxt.setCharacterWidth(60);
      new TextSaveButtonListener(descTxt, saveDesc);
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
    }

    {
      final Label memberHdr = new Label(Util.C.headingMembers());
      memberHdr.setStyleName("gerrit-SmallHeading");
      add(memberHdr);
    }

    {
      final FlowPanel fp = new FlowPanel();
      fp.setStyleName("gerrit-ProjectWatchPanel-AddPanel");

      nameTxtBox = new TextBox();
      nameTxt = new SuggestBox(new AccountSuggestOracle(), nameTxtBox);
      nameTxtBox.setVisibleLength(50);
      nameTxtBox.setText(Util.C.defaultAccountName());
      nameTxtBox.addStyleName("gerrit-InputFieldTypeHint");
      nameTxtBox.addFocusListener(new FocusListenerAdapter() {
        @Override
        public void onFocus(Widget sender) {
          if (Util.C.defaultAccountName().equals(nameTxtBox.getText())) {
            nameTxtBox.setText("");
            nameTxtBox.removeStyleName("gerrit-InputFieldTypeHint");
          }
        }

        @Override
        public void onLostFocus(Widget sender) {
          if ("".equals(nameTxtBox.getText())) {
            nameTxtBox.setText(Util.C.defaultAccountName());
            nameTxtBox.addStyleName("gerrit-InputFieldTypeHint");
          }
        }
      });
      fp.add(nameTxt);

      addMember = new Button(Util.C.buttonAddGroupMember());
      addMember.addClickListener(new ClickListener() {
        public void onClick(final Widget sender) {
          doAddNew();
        }
      });
      fp.add(addMember);
      add(fp);
    }

    members = new MemberTable();
    add(members);
    {
      final FlowPanel fp = new FlowPanel();
      delMember = new Button(Util.C.buttonDeleteGroupMembers());
      delMember.addClickListener(new ClickListener() {
        public void onClick(final Widget sender) {
          members.deleteChecked();
        }
      });
      fp.add(delMember);
      add(fp);
    }
  }

  private void display(final AccountGroupDetail result) {
    setTitleText(Util.M.group(result.group.getName()));
    descTxt.setText(result.group.getDescription());
    accounts = result.accounts;
    members.display(result.members);
    members.finishDisplay(true);
  }

  void doAddNew() {
    final String nameEmail = nameTxt.getText();
    if (nameEmail == null || nameEmail.length() == 0) {
      return;
    }

    addMember.setEnabled(false);
    Util.GROUP_SVC.addGroupMember(groupId, nameEmail,
        new GerritCallback<AccountGroupDetail>() {
          public void onSuccess(final AccountGroupDetail result) {
            addMember.setEnabled(true);
            nameTxt.setText("");
            if (result.accounts != null && result.members != null) {
              accounts.merge(result.accounts);
              for (final AccountGroupMember m : result.members) {
                members.insertMember(m);
              }
            }
          }

          @Override
          public void onFailure(final Throwable caught) {
            addMember.setEnabled(true);
            super.onFailure(caught);
          }
        });
  }

  private class MemberTable extends FancyFlexTable<AccountGroupMember> {
    MemberTable() {
      table.setText(0, 2, Util.C.columnMember());
      table.setText(0, 3, Util.C.columnEmailAddress());
      table.setText(0, 4, Util.C.columnOwner());
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
      fmt.addStyleName(0, 4, S_ICON_HEADER);
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
      populate(row, k);
    }

    void display(final List<AccountGroupMember> result) {
      while (1 < table.getRowCount())
        table.removeRow(table.getRowCount() - 1);

      for (final AccountGroupMember k : result) {
        final int row = table.getRowCount();
        table.insertRow(row);
        populate(row, k);
      }
    }

    void populate(final int row, final AccountGroupMember k) {
      final Account.Id accountId = k.getAccountId();
      table.setWidget(row, 1, new CheckBox());
      table.setWidget(row, 2, AccountDashboardLink.link(accounts, accountId));
      table.setText(row, 3, accounts.get(accountId).getPreferredEmail());

      final CheckBox owner = new CheckBox();
      owner.setChecked(k.isGroupOwner());
      owner.addClickListener(new ClickListener() {
        public void onClick(Widget sender) {
          final boolean oldValue = k.isGroupOwner();
          final boolean newValue = owner.isChecked();
          Util.GROUP_SVC.changeGroupOwner(k.getKey(), newValue,
              new GerritCallback<VoidResult>() {
                public void onSuccess(final VoidResult result) {
                  k.setGroupOwner(newValue);
                }

                @Override
                public void onFailure(final Throwable caught) {
                  owner.setChecked(oldValue);
                  k.setGroupOwner(oldValue);
                  super.onFailure(caught);
                }
              });
        }
      });
      table.setWidget(row, 4, owner);

      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      fmt.addStyleName(row, 1, S_ICON_CELL);
      fmt.addStyleName(row, 2, S_DATA_CELL);
      fmt.addStyleName(row, 3, S_DATA_CELL);
      fmt.addStyleName(row, 4, S_ICON_CELL);

      setRowItem(row, k);
    }
  }
}
