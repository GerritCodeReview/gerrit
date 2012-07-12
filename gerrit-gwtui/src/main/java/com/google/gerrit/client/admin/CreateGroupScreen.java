// Copyright (C) 2012 The Android Open Source Project
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

import static com.google.gerrit.common.data.GlobalCapability.CREATE_GROUP;

import com.google.gerrit.client.Dispatcher;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.NotFoundScreen;
import com.google.gerrit.client.account.AccountCapabilities;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.OnEditEnabler;
import com.google.gerrit.client.ui.Screen;
import com.google.gerrit.client.ui.SmallHeading;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwtexpui.globalkey.client.NpTextBox;

public class CreateGroupScreen extends Screen {

  private VerticalPanel addPanel;
  private NpTextBox addTxt;
  private Button addNew;

  public CreateGroupScreen() {
    super();
    setRequiresSignIn(true);
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    addPanel.setVisible(false);
    display();
    AccountCapabilities.all(new GerritCallback<AccountCapabilities>() {
      @Override
      public void onSuccess(AccountCapabilities ac) {
        if (ac.canPerform(CREATE_GROUP)) {
          addPanel.setVisible(true);
        } else {
          Gerrit.display(PageLinks.ADMIN_CREATE_GROUP, new NotFoundScreen());
        }
      }
    }, CREATE_GROUP);
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();
    setPageTitle(Util.C.createGroupTitle());
    addCreateGroupPanel();
  }

  private void addCreateGroupPanel() {
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
    addPanel.add(addNew);
    add(addPanel);

    new OnEditEnabler(addNew, addTxt);
  }

  private void doCreateGroup() {
    final String newName = addTxt.getText();
    if (newName == null || newName.length() == 0) {
      return;
    }

    addNew.setEnabled(false);
    Util.GROUP_SVC.createGroup(newName, new GerritCallback<AccountGroup.Id>() {
      public void onSuccess(final AccountGroup.Id result) {
        History.newItem(Dispatcher.toGroup(result));
      }

      @Override
      public void onFailure(Throwable caught) {
        super.onFailure(caught);
        addNew.setEnabled(true);
      }
    });
  }
}
