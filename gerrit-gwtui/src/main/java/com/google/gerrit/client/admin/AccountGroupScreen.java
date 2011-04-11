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
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.AccountDashboardLink;
import com.google.gerrit.client.ui.AccountGroupSuggestOracle;
import com.google.gerrit.client.ui.AccountScreen;
import com.google.gerrit.client.ui.AddMemberBox;
import com.google.gerrit.client.ui.AddIncludedGroupBox;
import com.google.gerrit.client.ui.FancyFlexTable;
import com.google.gerrit.client.ui.Hyperlink;
import com.google.gerrit.client.ui.OnEditEnabler;
import com.google.gerrit.client.ui.RPCSuggestOracle;
import com.google.gerrit.client.ui.SmallHeading;
import com.google.gerrit.common.data.AccountInfoCache;
import com.google.gerrit.common.data.GroupDetail;
import com.google.gerrit.common.data.GroupOptions;
import com.google.gerrit.common.data.GroupInfo;
import com.google.gerrit.common.data.GroupInfoCache;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.AccountGroupInclude;
import com.google.gerrit.reviewdb.AccountGroupMember;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.SuggestBox;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;
import com.google.gwtexpui.globalkey.client.NpTextArea;
import com.google.gwtexpui.globalkey.client.NpTextBox;
import com.google.gwtjsonrpc.client.VoidResult;

import java.util.HashSet;
import java.util.List;

public class AccountGroupScreen extends AccountScreen {
  private final AccountGroup.Id groupId;
  private AccountInfoCache accounts = AccountInfoCache.empty();
  private GroupInfoCache groups = GroupInfoCache.empty();
  private MemberTable members;
  private IncludeTable includes;

  private NpTextBox groupNameTxt;
  private Button saveName;

  private NpTextBox ownerTxtBox;
  private SuggestBox ownerTxt;
  private Button saveOwner;

  private NpTextArea descTxt;
  private Button saveDesc;

  private Label typeSystem;
  private ListBox typeSelect;
  private Button saveType;

  private Panel memberPanel;
  private AddMemberBox addMemberBox;
  private Button delMember;

  private Panel includePanel;
  private AddIncludedGroupBox addIncludeBox;
  private Button delInclude;

  private Panel externalPanel;
  private Label externalName;
  private NpTextBox externalNameFilter;
  private Button externalNameSearch;
  private Grid externalMatches;

  private Panel groupOptionsPanel;
  private CheckBox visibleToAllCheckBox;
  private CheckBox emailOnlyAuthors;
  private Button saveGroupOptions;

  public AccountGroupScreen(final AccountGroup.Id toShow) {
    groupId = toShow;
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    Util.GROUP_SVC.groupDetail(groupId, new ScreenLoadCallback<GroupDetail>(
        this) {
      @Override
      protected void preDisplay(final GroupDetail result) {
        enableForm(result.canModify);
        saveName.setVisible(result.canModify);
        saveOwner.setVisible(result.canModify);
        saveDesc.setVisible(result.canModify);
        saveGroupOptions.setVisible(result.canModify);
        delMember.setVisible(result.canModify);
        members.setEnabled(result.canModify);
        saveType.setVisible(result.canModify);
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
    initGroupOptions();
    initGroupType();

    initMemberList();
    initIncludeList();
    initExternal();
  }

  private void enableForm(final boolean canModify) {
    groupNameTxt.setEnabled(canModify);
    ownerTxtBox.setEnabled(canModify);
    descTxt.setEnabled(canModify);
    typeSelect.setEnabled(canModify);
    addMemberBox.setEnabled(canModify);
    externalNameFilter.setEnabled(canModify);
    externalNameSearch.setEnabled(canModify);
    visibleToAllCheckBox.setEnabled(canModify);
    emailOnlyAuthors.setEnabled(canModify);
  }

  private void initName() {
    final VerticalPanel groupNamePanel = new VerticalPanel();
    groupNameTxt = new NpTextBox();
    groupNameTxt.setVisibleLength(60);
    groupNamePanel.add(groupNameTxt);

    saveName = new Button(Util.C.buttonRenameGroup());
    saveName.setEnabled(false);
    saveName.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        final String newName = groupNameTxt.getText().trim();
        Util.GROUP_SVC.renameGroup(groupId, newName,
            new GerritCallback<GroupDetail>() {
              public void onSuccess(final GroupDetail groupDetail) {
                saveName.setEnabled(false);
                display(groupDetail);
              }
            });
      }
    });
    groupNamePanel.add(saveName);
    add(groupNamePanel);

    new OnEditEnabler(saveName, groupNameTxt);
  }

  private void initOwner() {
    final VerticalPanel ownerPanel = new VerticalPanel();
    ownerPanel.add(new SmallHeading(Util.C.headingOwner()));

    ownerTxtBox = new NpTextBox();
    ownerTxtBox.setVisibleLength(60);
    ownerTxt = new SuggestBox(new RPCSuggestOracle(
        new AccountGroupSuggestOracle()), ownerTxtBox);
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

    new OnEditEnabler(saveOwner, ownerTxtBox);
  }

  private void initDescription() {
    final VerticalPanel vp = new VerticalPanel();
    vp.add(new SmallHeading(Util.C.headingDescription()));

    descTxt = new NpTextArea();
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

    new OnEditEnabler(saveDesc, descTxt);
  }

  private void initGroupOptions() {
    groupOptionsPanel = new VerticalPanel();
    groupOptionsPanel.add(new SmallHeading(Util.C.headingGroupOptions()));

    visibleToAllCheckBox = new CheckBox(Util.C.isVisibleToAll());
    visibleToAllCheckBox.setEnabled(false);
    groupOptionsPanel.add(visibleToAllCheckBox);

    emailOnlyAuthors = new CheckBox(Util.C.emailOnlyAuthors());
    emailOnlyAuthors.setEnabled(false);

    final VerticalPanel vp = new VerticalPanel();
    vp.add(new Label(Util.C.descriptionNotifications()));
    vp.add(emailOnlyAuthors);
    groupOptionsPanel.add(vp);

    saveGroupOptions = new Button(Util.C.buttonSaveGroupOptions());
    saveGroupOptions.setEnabled(false);
    saveGroupOptions.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        final GroupOptions groupOptions =
            new GroupOptions(visibleToAllCheckBox.getValue(),
              emailOnlyAuthors.getValue());
        Util.GROUP_SVC.changeGroupOptions(groupId, groupOptions,
            new GerritCallback<VoidResult>() {
              public void onSuccess(final VoidResult result) {
                saveGroupOptions.setEnabled(false);
              }
            });
      }
    });
    groupOptionsPanel.add(saveGroupOptions);

    add(groupOptionsPanel);

    final OnEditEnabler enabler = new OnEditEnabler(saveGroupOptions);
    enabler.listenTo(visibleToAllCheckBox);
    enabler.listenTo(emailOnlyAuthors);
  }

  private void initGroupType() {
    typeSystem = new Label(Util.C.groupType_SYSTEM());

    typeSelect = new ListBox();
    typeSelect.addItem(Util.C.groupType_INTERNAL(), AccountGroup.Type.INTERNAL.name());
    typeSelect.addItem(Util.C.groupType_LDAP(), AccountGroup.Type.LDAP.name());
    typeSelect.addChangeHandler(new ChangeHandler() {
      @Override
      public void onChange(ChangeEvent event) {
        saveType.setEnabled(true);
      }
    });

    saveType = new Button(Util.C.buttonChangeGroupType());
    saveType.setEnabled(false);
    saveType.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        onSaveType();
      }
    });

    switch (Gerrit.getConfig().getAuthType()) {
      case HTTP_LDAP:
      case LDAP:
      case LDAP_BIND:
      case CLIENT_SSL_CERT_LDAP:
        break;
      default:
        return;
    }

    final VerticalPanel fp = new VerticalPanel();
    fp.add(new SmallHeading(Util.C.headingGroupType()));
    fp.add(typeSystem);
    fp.add(typeSelect);
    fp.add(saveType);
    add(fp);
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

  private void initExternal() {
    externalName = new Label();

    externalNameFilter = new NpTextBox();
    externalNameFilter.setVisibleLength(30);
    externalNameFilter.addKeyPressHandler(new KeyPressHandler() {
      @Override
      public void onKeyPress(final KeyPressEvent event) {
        if (event.getCharCode() == KeyCodes.KEY_ENTER) {
          doExternalSearch();
        }
      }
    });

    externalNameSearch = new Button(Gerrit.C.searchButton());
    externalNameSearch.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        doExternalSearch();
      }
    });

    externalMatches = new Grid();
    externalMatches.setStyleName(Gerrit.RESOURCES.css().infoTable());
    externalMatches.setVisible(false);

    final FlowPanel searchLine = new FlowPanel();
    searchLine.add(externalNameFilter);
    searchLine.add(externalNameSearch);

    externalPanel = new VerticalPanel();
    externalPanel.add(new SmallHeading(Util.C.headingExternalGroup()));
    externalPanel.add(externalName);
    externalPanel.add(searchLine);
    externalPanel.add(externalMatches);
    add(externalPanel);
  }

  private void setType(final AccountGroup.Type newType) {
    final boolean system = newType == AccountGroup.Type.SYSTEM;

    typeSystem.setVisible(system);
    typeSelect.setVisible(!system);
    saveType.setVisible(!system);
    memberPanel.setVisible(newType == AccountGroup.Type.INTERNAL);
    includePanel.setVisible(newType == AccountGroup.Type.INTERNAL);
    externalPanel.setVisible(newType == AccountGroup.Type.LDAP);
    externalNameFilter.setText(groupNameTxt.getText());

    if (!system) {
      for (int i = 0; i < typeSelect.getItemCount(); i++) {
        if (newType.name().equals(typeSelect.getValue(i))) {
          typeSelect.setSelectedIndex(i);
          break;
        }
      }
    }

    saveType.setEnabled(false);
  }

  private void onSaveType() {
    final int idx = typeSelect.getSelectedIndex();
    final AccountGroup.Type newType =
        AccountGroup.Type.valueOf(typeSelect.getValue(idx));

    typeSelect.setEnabled(false);
    saveType.setEnabled(false);

    Util.GROUP_SVC.changeGroupType(groupId, newType,
        new GerritCallback<VoidResult>() {
          @Override
          public void onSuccess(VoidResult result) {
            typeSelect.setEnabled(true);
            setType(newType);
          }

          @Override
          public void onFailure(Throwable caught) {
            typeSelect.setEnabled(true);
            saveType.setEnabled(true);
            super.onFailure(caught);
          }
        });
  }

  private void doExternalSearch() {
    externalNameFilter.setEnabled(false);
    externalNameSearch.setEnabled(false);
    Util.GROUP_SVC.searchExternalGroups(externalNameFilter.getText(),
        new GerritCallback<List<AccountGroup.ExternalNameKey>>() {
          @Override
          public void onSuccess(List<AccountGroup.ExternalNameKey> result) {
            final CellFormatter fmt = externalMatches.getCellFormatter();

            if (result.isEmpty()) {
              externalMatches.resize(1, 1);
              externalMatches.setText(0, 0, Util.C.errorNoMatchingGroups());
              fmt.setStyleName(0, 0, Gerrit.RESOURCES.css().header());
              return;
            }

            externalMatches.resize(1 + result.size(), 2);

            externalMatches.setText(0, 0, Util.C.columnGroupName());
            externalMatches.setText(0, 1, "");
            fmt.setStyleName(0, 0, Gerrit.RESOURCES.css().header());
            fmt.setStyleName(0, 1, Gerrit.RESOURCES.css().header());

            for (int row = 0; row < result.size(); row++) {
              final AccountGroup.ExternalNameKey key = result.get(row);
              final Button b = new Button(Util.C.buttonSelectGroup());
              b.addClickHandler(new ClickHandler() {
                @Override
                public void onClick(ClickEvent event) {
                  setExternalGroup(key);
                }
              });
              externalMatches.setText(1 + row, 0, key.get());
              externalMatches.setWidget(1 + row, 1, b);
              fmt.setStyleName(1 + row, 1, Gerrit.RESOURCES.css().rightmost());
            }
            externalMatches.setVisible(true);

            externalNameFilter.setEnabled(true);
            externalNameSearch.setEnabled(true);
          }

          @Override
          public void onFailure(Throwable caught) {
            externalNameFilter.setEnabled(true);
            externalNameSearch.setEnabled(true);
            super.onFailure(caught);
          }
        });
  }

  private void setExternalGroup(final AccountGroup.ExternalNameKey key) {
    externalMatches.setVisible(false);

    Util.GROUP_SVC.changeExternalGroup(groupId, key,
        new GerritCallback<VoidResult>() {
          @Override
          public void onSuccess(VoidResult result) {
            externalName.setText(key.get());
          }

          @Override
          public void onFailure(Throwable caught) {
            externalMatches.setVisible(true);
            super.onFailure(caught);
          }
        });
  }

  private void display(final GroupDetail result) {
    final AccountGroup group = result.group;
    setPageTitle(Util.M.group(group.getName()));
    groupNameTxt.setText(group.getName());
    if (result.ownerGroup != null) {
      ownerTxt.setText(result.ownerGroup.getName());
    } else {
      ownerTxt.setText(Util.M.deletedGroup(group.getOwnerGroupId().get()));
    }
    descTxt.setText(group.getDescription());

    visibleToAllCheckBox.setValue(group.isVisibleToAll());
    visibleToAllCheckBox.setEnabled(true);
    emailOnlyAuthors.setValue(group.isEmailOnlyAuthors());
    emailOnlyAuthors.setEnabled(true);

    switch (group.getType()) {
      case INTERNAL:
        accounts = result.accounts;
        groups = result.groups;
        members.display(result.members);
        includes.display(result.includes);
        break;

      case LDAP:
        externalName.setText(group.getExternalNameKey() != null ? group
            .getExternalNameKey().get() : Util.C.noGroupSelected());
        break;
    }

    setType(group.getType());
  }

  void doAddNewMember() {
    final String nameEmail = addMemberBox.getText();
    if (nameEmail.length() == 0) {
      return;
    }

    addMemberBox.setEnabled(false);
    Util.GROUP_SVC.addGroupMember(groupId, nameEmail,
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
    Util.GROUP_SVC.addGroupInclude(groupId, groupName,
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
        Util.GROUP_SVC.deleteGroupMembers(groupId, ids,
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
    IncludeTable() {
      table.setText(0, 2, Util.C.columnGroupName());
      table.setText(0, 3, Util.C.columnGroupDescription());

      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      fmt.addStyleName(0, 1, Gerrit.RESOURCES.css().iconHeader());
      fmt.addStyleName(0, 2, Gerrit.RESOURCES.css().dataHeader());
      fmt.addStyleName(0, 3, Gerrit.RESOURCES.css().dataHeader());
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
        Util.GROUP_SVC.deleteGroupIncludes(groupId, keys,
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

    void insertMember(final AccountGroupInclude k) {
      final int row = table.getRowCount();
      table.insertRow(row);
      applyDataRowStyle(row);
      populate(row, k);
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
      table.setWidget(row, 1, new CheckBox());
      table.setWidget(row, 2, new Hyperlink(group.getName(), Dispatcher
          .toAccountGroup(id)));
      table.setText(row, 3, groups.get(id).getDescription());

      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      fmt.addStyleName(row, 1, Gerrit.RESOURCES.css().iconCell());
      fmt.addStyleName(row, 2, Gerrit.RESOURCES.css().dataCell());
      fmt.addStyleName(row, 3, Gerrit.RESOURCES.css().dataCell());

      setRowItem(row, k);
    }
  }
}
