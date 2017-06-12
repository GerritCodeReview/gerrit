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
import com.google.gerrit.client.VoidResult;
import com.google.gerrit.client.groups.GroupApi;
import com.google.gerrit.client.info.GroupInfo;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.AccountGroupSuggestOracle;
import com.google.gerrit.client.ui.OnEditEnabler;
import com.google.gerrit.client.ui.RemoteSuggestBox;
import com.google.gerrit.client.ui.SmallHeading;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwtexpui.clippy.client.CopyableLabel;
import com.google.gwtexpui.globalkey.client.NpTextArea;
import com.google.gwtexpui.globalkey.client.NpTextBox;

public class AccountGroupInfoScreen extends AccountGroupScreen {
  private CopyableLabel groupUUIDLabel;

  private NpTextBox groupNameTxt;
  private Button saveName;

  private RemoteSuggestBox ownerTxt;
  private Button saveOwner;

  private NpTextArea descTxt;
  private Button saveDesc;

  private CheckBox visibleToAllCheckBox;
  private Button saveGroupOptions;

  public AccountGroupInfoScreen(final GroupInfo toShow, final String token) {
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
  }

  private void enableForm(final boolean canModify) {
    groupNameTxt.setEnabled(canModify);
    ownerTxt.setEnabled(canModify);
    descTxt.setEnabled(canModify);
    visibleToAllCheckBox.setEnabled(canModify);
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
    saveName.addClickHandler(
        new ClickHandler() {
          @Override
          public void onClick(final ClickEvent event) {
            final String newName = groupNameTxt.getText().trim();
            GroupApi.renameGroup(
                getGroupUUID(),
                newName,
                new GerritCallback<com.google.gerrit.client.VoidResult>() {
                  @Override
                  public void onSuccess(final com.google.gerrit.client.VoidResult result) {
                    saveName.setEnabled(false);
                    setPageTitle(Util.M.group(newName));
                    groupNameTxt.setText(newName);
                    if (getGroupUUID().equals(getOwnerGroupUUID())) {
                      ownerTxt.setText(newName);
                    }
                  }
                });
          }
        });
    groupNamePanel.add(saveName);
    add(groupNamePanel);
  }

  private void initOwner() {
    final VerticalPanel ownerPanel = new VerticalPanel();
    ownerPanel.setStyleName(Gerrit.RESOURCES.css().groupOwnerPanel());
    ownerPanel.add(new SmallHeading(Util.C.headingOwner()));

    final AccountGroupSuggestOracle accountGroupOracle = new AccountGroupSuggestOracle();
    ownerTxt = new RemoteSuggestBox(accountGroupOracle);
    ownerTxt.setStyleName(Gerrit.RESOURCES.css().groupOwnerTextBox());
    ownerTxt.setVisibleLength(60);
    ownerPanel.add(ownerTxt);

    saveOwner = new Button(Util.C.buttonChangeGroupOwner());
    saveOwner.setEnabled(false);
    saveOwner.addClickHandler(
        new ClickHandler() {
          @Override
          public void onClick(final ClickEvent event) {
            final String newOwner = ownerTxt.getText().trim();
            if (newOwner.length() > 0) {
              AccountGroup.UUID ownerUuid = accountGroupOracle.getUUID(newOwner);
              String ownerId = ownerUuid != null ? ownerUuid.get() : newOwner;
              GroupApi.setGroupOwner(
                  getGroupUUID(),
                  ownerId,
                  new GerritCallback<GroupInfo>() {
                    @Override
                    public void onSuccess(final GroupInfo result) {
                      updateOwnerGroup(result);
                      saveOwner.setEnabled(false);
                    }
                  });
            }
          }
        });
    ownerPanel.add(saveOwner);
    add(ownerPanel);
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
    saveDesc.addClickHandler(
        new ClickHandler() {
          @Override
          public void onClick(final ClickEvent event) {
            final String txt = descTxt.getText().trim();
            GroupApi.setGroupDescription(
                getGroupUUID(),
                txt,
                new GerritCallback<VoidResult>() {
                  @Override
                  public void onSuccess(final VoidResult result) {
                    saveDesc.setEnabled(false);
                  }
                });
          }
        });
    vp.add(saveDesc);
    add(vp);
  }

  private void initGroupOptions() {
    final VerticalPanel groupOptionsPanel = new VerticalPanel();

    final VerticalPanel vp = new VerticalPanel();
    vp.setStyleName(Gerrit.RESOURCES.css().groupOptionsPanel());
    vp.add(new SmallHeading(Util.C.headingGroupOptions()));

    visibleToAllCheckBox = new CheckBox(Util.C.isVisibleToAll());
    vp.add(visibleToAllCheckBox);
    groupOptionsPanel.add(vp);

    saveGroupOptions = new Button(Util.C.buttonSaveGroupOptions());
    saveGroupOptions.setEnabled(false);
    saveGroupOptions.addClickHandler(
        new ClickHandler() {
          @Override
          public void onClick(final ClickEvent event) {
            GroupApi.setGroupOptions(
                getGroupUUID(),
                visibleToAllCheckBox.getValue(),
                new GerritCallback<VoidResult>() {
                  @Override
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
  }

  @Override
  protected void display(final GroupInfo group, final boolean canModify) {
    groupUUIDLabel.setText(group.getGroupUUID().get());
    groupNameTxt.setText(group.name());
    ownerTxt.setText(
        group.owner() != null
            ? group.owner()
            : Util.M.deletedReference(group.getOwnerUUID().get()));
    descTxt.setText(group.description());
    visibleToAllCheckBox.setValue(group.options().isVisibleToAll());
    setMembersTabVisible(AccountGroup.isInternalGroup(group.getGroupUUID()));

    enableForm(canModify);
    saveName.setVisible(canModify);
    saveOwner.setVisible(canModify);
    saveDesc.setVisible(canModify);
    saveGroupOptions.setVisible(canModify);
    new OnEditEnabler(saveDesc, descTxt);
    new OnEditEnabler(saveName, groupNameTxt);
    new OnEditEnabler(saveOwner, ownerTxt.getTextBox());
  }
}
