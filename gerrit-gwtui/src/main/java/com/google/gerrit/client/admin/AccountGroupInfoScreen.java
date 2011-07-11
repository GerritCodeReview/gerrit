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

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.AccountGroupSuggestOracle;
import com.google.gerrit.client.ui.OnEditEnabler;
import com.google.gerrit.client.ui.RPCSuggestOracle;
import com.google.gerrit.client.ui.SmallHeading;
import com.google.gerrit.common.data.GroupDetail;
import com.google.gerrit.common.data.GroupOptions;
import com.google.gerrit.reviewdb.AccountGroup;
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
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.SuggestBox;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwtexpui.clippy.client.CopyableLabel;
import com.google.gwtexpui.globalkey.client.NpTextArea;
import com.google.gwtexpui.globalkey.client.NpTextBox;
import com.google.gwtjsonrpc.client.VoidResult;

import java.util.List;

public class AccountGroupInfoScreen extends AccountGroupScreen {
  private CopyableLabel groupUUIDLabel;

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

  private Panel externalPanel;
  private Label externalName;
  private NpTextBox externalNameFilter;
  private Button externalNameSearch;
  private Grid externalMatches;

  private Panel groupOptionsPanel;
  private CheckBox visibleToAllCheckBox;
  private CheckBox emailOnlyAuthors;
  private Button saveGroupOptions;

  public AccountGroupInfoScreen(final GroupDetail toShow, final String token) {
    super(toShow, token);
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();
    initUUID();
    initName();
    initOwner();
    initDescription();
    initGroupOptions();
    initGroupType();

    initExternal();
  }

  private void enableForm(final boolean canModify) {
    groupNameTxt.setEnabled(canModify);
    ownerTxtBox.setEnabled(canModify);
    descTxt.setEnabled(canModify);
    typeSelect.setEnabled(canModify);
    externalNameFilter.setEnabled(canModify);
    externalNameSearch.setEnabled(canModify);
    visibleToAllCheckBox.setEnabled(canModify);
    emailOnlyAuthors.setEnabled(canModify);
  }

  private void initUUID() {
    final VerticalPanel groupUUIDPanel = new VerticalPanel();
    groupUUIDPanel.setStyleName(Gerrit.RESOURCES.css().groupUUIDPanel());
    groupUUIDPanel.add(new SmallHeading(Util.C.headingGroupUUID()));
    groupUUIDLabel = new CopyableLabel("");
    groupUUIDPanel.add(groupUUIDLabel);
    add(groupUUIDPanel);
  }

  private void initName() {
    final VerticalPanel groupNamePanel = new VerticalPanel();
    groupNamePanel.setStyleName(Gerrit.RESOURCES.css().groupNamePanel());
    groupNameTxt = new NpTextBox();
    groupNameTxt.setStyleName(Gerrit.RESOURCES.css().groupNameTextBox());
    groupNameTxt.setVisibleLength(60);
    groupNamePanel.add(groupNameTxt);

    saveName = new Button(Util.C.buttonRenameGroup());
    saveName.setEnabled(false);
    saveName.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        final String newName = groupNameTxt.getText().trim();
        Util.GROUP_SVC.renameGroup(getGroupId(), newName,
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
    ownerPanel.setStyleName(Gerrit.RESOURCES.css().groupOwnerPanel());
    ownerPanel.add(new SmallHeading(Util.C.headingOwner()));

    ownerTxtBox = new NpTextBox();
    ownerTxtBox.setVisibleLength(60);
    ownerTxt = new SuggestBox(new RPCSuggestOracle(
        new AccountGroupSuggestOracle()), ownerTxtBox);
    ownerTxt.setStyleName(Gerrit.RESOURCES.css().groupOwnerTextBox());
    ownerPanel.add(ownerTxt);

    saveOwner = new Button(Util.C.buttonChangeGroupOwner());
    saveOwner.setEnabled(false);
    saveOwner.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        final String newOwner = ownerTxt.getText().trim();
        if (newOwner.length() > 0) {
          Util.GROUP_SVC.changeGroupOwner(getGroupId(), newOwner,
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
    vp.setStyleName(Gerrit.RESOURCES.css().groupDescriptionPanel());
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
        Util.GROUP_SVC.changeGroupDescription(getGroupId(), txt,
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
    groupOptionsPanel.setStyleName(Gerrit.RESOURCES.css().groupOptionsPanel());
    groupOptionsPanel.add(new SmallHeading(Util.C.headingGroupOptions()));

    visibleToAllCheckBox = new CheckBox(Util.C.isVisibleToAll());
    groupOptionsPanel.add(visibleToAllCheckBox);

    emailOnlyAuthors = new CheckBox(Util.C.emailOnlyAuthors());

    final VerticalPanel vp = new VerticalPanel();
    vp.setStyleName(Gerrit.RESOURCES.css()
        .groupOptionsNotificationsDescriptionPanel());
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
        Util.GROUP_SVC.changeGroupOptions(getGroupId(), groupOptions,
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
    typeSelect.setStyleName(Gerrit.RESOURCES.css().groupTypeSelectListBox());
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
    fp.setStyleName(Gerrit.RESOURCES.css().groupTypePanel());
    fp.add(new SmallHeading(Util.C.headingGroupType()));
    fp.add(typeSystem);
    fp.add(typeSelect);
    fp.add(saveType);
    add(fp);
  }

  private void initExternal() {
    externalName = new Label();

    externalNameFilter = new NpTextBox();
    externalNameFilter.setStyleName(Gerrit.RESOURCES.css()
        .groupExternalNameFilterTextBox());
    externalNameFilter.setVisibleLength(30);
    externalNameFilter.addKeyPressHandler(new KeyPressHandler() {
      @Override
      public void onKeyPress(final KeyPressEvent event) {
        if (event.getNativeEvent().getKeyCode() == KeyCodes.KEY_ENTER) {
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

    setMembersTabVisible(newType == AccountGroup.Type.INTERNAL);
  }

  private void onSaveType() {
    final int idx = typeSelect.getSelectedIndex();
    final AccountGroup.Type newType =
        AccountGroup.Type.valueOf(typeSelect.getValue(idx));

    typeSelect.setEnabled(false);
    saveType.setEnabled(false);

    Util.GROUP_SVC.changeGroupType(getGroupId(), newType,
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

    Util.GROUP_SVC.changeExternalGroup(getGroupId(), key,
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

  @Override
  protected void display(final GroupDetail groupDetail) {
    final AccountGroup group = groupDetail.group;
    groupUUIDLabel.setText(group.getGroupUUID().get());
    groupNameTxt.setText(group.getName());
    if (groupDetail.ownerGroup != null) {
      ownerTxt.setText(groupDetail.ownerGroup.getName());
    } else {
      ownerTxt.setText(Util.M.deletedGroup(group.getOwnerGroupId().get()));
    }
    descTxt.setText(group.getDescription());

    visibleToAllCheckBox.setValue(group.isVisibleToAll());
    emailOnlyAuthors.setValue(group.isEmailOnlyAuthors());

    switch (group.getType()) {
      case LDAP:
        externalName.setText(group.getExternalNameKey() != null ? group
            .getExternalNameKey().get() : Util.C.noGroupSelected());
        break;
    }

    setType(group.getType());

    enableForm(groupDetail.canModify);
    saveName.setVisible(groupDetail.canModify);
    saveOwner.setVisible(groupDetail.canModify);
    saveDesc.setVisible(groupDetail.canModify);
    saveGroupOptions.setVisible(groupDetail.canModify);
    saveType.setVisible(groupDetail.canModify);
  }
}
