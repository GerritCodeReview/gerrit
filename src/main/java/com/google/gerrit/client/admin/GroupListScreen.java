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
import com.google.gerrit.client.ui.SmallHeading;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwtexpui.globalkey.client.NpTextBox;

import java.util.List;

public class GroupListScreen extends AccountScreen {
  private GroupTable groups;

  private NpTextBox addTxt;
  private Button addNew;

  @Override
  protected void onLoad() {
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

    groups = new GroupTable(true /* hyperlink to admin */, Link.ADMIN_GROUPS);
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
}
