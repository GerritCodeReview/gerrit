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
import com.google.gerrit.client.groups.GroupApi;
import com.google.gerrit.client.info.GroupInfo;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.OnEditEnabler;
import com.google.gerrit.client.ui.Screen;
import com.google.gerrit.client.ui.SmallHeading;
import com.google.gerrit.common.PageLinks;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwtexpui.globalkey.client.NpTextBox;

public class CreateGroupScreen extends Screen {

  private NpTextBox addTxt;
  private Button addNew;

  public CreateGroupScreen() {
    super();
    setRequiresSignIn(true);
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    AccountCapabilities.all(
        new GerritCallback<AccountCapabilities>() {
          @Override
          public void onSuccess(AccountCapabilities ac) {
            if (ac.canPerform(CREATE_GROUP)) {
              display();
            } else {
              Gerrit.display(PageLinks.ADMIN_CREATE_GROUP, new NotFoundScreen());
            }
          }
        },
        CREATE_GROUP);
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();
    setPageTitle(AdminConstants.I.createGroupTitle());
    addCreateGroupPanel();
  }

  @Override
  public void onShowView() {
    super.onShowView();
    if (addTxt != null) {
      addTxt.setFocus(true);
    }
  }

  private void addCreateGroupPanel() {
    VerticalPanel addPanel = new VerticalPanel();
    addPanel.setStyleName(Gerrit.RESOURCES.css().addSshKeyPanel());
    addPanel.add(new SmallHeading(AdminConstants.I.headingCreateGroup()));

    addTxt =
        new NpTextBox() {
          @Override
          public void onBrowserEvent(Event event) {
            super.onBrowserEvent(event);
            if (event.getTypeInt() == Event.ONPASTE) {
              Scheduler.get()
                  .scheduleDeferred(
                      new ScheduledCommand() {
                        @Override
                        public void execute() {
                          if (addTxt.getValue().trim().length() != 0) {
                            addNew.setEnabled(true);
                          }
                        }
                      });
            }
          }
        };
    addTxt.sinkEvents(Event.ONPASTE);

    addTxt.setVisibleLength(60);
    addTxt.addKeyPressHandler(
        new KeyPressHandler() {
          @Override
          public void onKeyPress(KeyPressEvent event) {
            if (event.getNativeEvent().getKeyCode() == KeyCodes.KEY_ENTER) {
              doCreateGroup();
            }
          }
        });
    addPanel.add(addTxt);

    addNew = new Button(AdminConstants.I.buttonCreateGroup());
    addNew.setEnabled(false);
    addNew.addClickHandler(
        new ClickHandler() {
          @Override
          public void onClick(ClickEvent event) {
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
    GroupApi.createGroup(
        newName,
        new GerritCallback<GroupInfo>() {
          @Override
          public void onSuccess(GroupInfo result) {
            History.newItem(Dispatcher.toGroup(result.getGroupId(), AccountGroupScreen.MEMBERS));
          }

          @Override
          public void onFailure(Throwable caught) {
            super.onFailure(caught);
            addNew.setEnabled(true);
          }
        });
  }
}
