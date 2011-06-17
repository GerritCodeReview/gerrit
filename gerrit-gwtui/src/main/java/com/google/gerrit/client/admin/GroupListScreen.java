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
import com.google.gerrit.client.ui.AccountScreen;
import com.google.gerrit.client.ui.OnEditEnabler;
import com.google.gerrit.client.ui.SmallHeading;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.common.data.GroupList;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gwt.event.dom.client.BlurEvent;
import com.google.gwt.event.dom.client.BlurHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.FocusEvent;
import com.google.gwt.event.dom.client.FocusHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwtexpui.globalkey.client.NpTextBox;

public class GroupListScreen extends AccountScreen {
  private GroupTable groups;

  private VerticalPanel addPanel;
  private NpTextBox addTxt;
  private Button addNew;

  @Override
  protected void onLoad() {
    super.onLoad();
    Util.GROUP_SVC
        .visibleGroups(new ScreenLoadCallback<GroupList>(this) {
          @Override
          protected void preDisplay(GroupList result) {
            addPanel.setVisible(result.isCanCreateGroup());
            groups.display(result.getGroups());
            groups.finishDisplay();
          }
        });
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();
    setPageTitle(Util.C.groupListTitle());

    groups = new GroupTable(true /* hyperlink to admin */, PageLinks.ADMIN_GROUPS);
    add(groups);

    addPanel = new VerticalPanel();
    addPanel.setStyleName(Gerrit.RESOURCES.css().addSshKeyPanel());
    addPanel.add(new SmallHeading(Util.C.headingCreateGroup()));

    addTxt = new NpTextBox();
    addTxt.setVisibleLength(60);
    addTxt.addKeyPressHandler(new KeyPressHandler() {
      @Override
      public void onKeyPress(KeyPressEvent event) {
        if (event.getNativeEvent().getKeyCode() == KeyCodes.KEY_ENTER) {
          doCreateGroup();
        }
      }
    });
    addPanel.add(addTxt);

    addNew = new Button(Util.C.buttonCreateGroup());
    addNew.setEnabled(false);
    addNew.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        doCreateGroup();
      }
    });
    addNew.addFocusHandler(new FocusHandler() {
      @Override
      public void onFocus(FocusEvent event) {
        // unregister the keys for the 'groups' table so that pressing ENTER
        // when the 'addNew' button has the focus triggers the button (if the
        // keys for the 'groups' table would not be unregistered the 'addNew'
        // button would not be triggered on ENTER but the group which is
        // selected in the 'groups' table would be opened)
        groups.setRegisterKeys(false);
      }
    });
    addNew.addBlurHandler(new BlurHandler() {
      @Override
      public void onBlur(BlurEvent event) {
        // re-register the keys for the 'groups' table when the 'addNew' button
        // gets blurred
        groups.setRegisterKeys(true);
      }
    });
    addPanel.add(addNew);
    add(addPanel);

    new OnEditEnabler(addNew, addTxt);
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

    addNew.setEnabled(false);
    Util.GROUP_SVC.createGroup(newName, new GerritCallback<AccountGroup.Id>() {
      public void onSuccess(final AccountGroup.Id result) {
        History.newItem(Dispatcher.toAccountGroup(result));
      }

      @Override
      public void onFailure(Throwable caught) {
        super.onFailure(caught);
        addNew.setEnabled(true);
      }
    });
  }
}
