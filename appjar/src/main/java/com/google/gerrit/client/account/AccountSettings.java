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

package com.google.gerrit.client.account;

import com.google.gerrit.client.FormatUtil;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.AccountScreen;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.TabPanel;
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;

public class AccountSettings extends AccountScreen {
  private final Grid info;

  private TabPanel tabs;

  private Panel prefsPanel;
  private Panel agreementsPanel;
  private SshKeyPanel keysPanel;

  public AccountSettings() {
    super(Util.C.accountSettingsHeading());

    info = new Grid(3, 2);
    info.setStyleName("gerrit-InfoBlock");
    info.addStyleName("gerrit-AccountInfoBlock");
    add(info);

    infoRow(0, Util.C.fullName());
    infoRow(1, Util.C.preferredEmail());
    infoRow(2, Util.C.registeredOn());

    final CellFormatter fmt = info.getCellFormatter();
    fmt.addStyleName(0, 0, "topmost");
    fmt.addStyleName(0, 1, "topmost");
    fmt.addStyleName(2, 0, "bottomheader");

    prefsPanel = new FlowPanel();
    prefsPanel.add(new Label("Not Implemented"));

    keysPanel = new SshKeyPanel();

    agreementsPanel = new FlowPanel();
    agreementsPanel.add(new Label("Not Implemented"));

    tabs = new TabPanel();
    tabs.setWidth("100%");
    tabs.add(prefsPanel, Util.C.tabPreferences());
    tabs.add(keysPanel, Util.C.tabSshKeys());
    tabs.add(agreementsPanel, Util.C.tabAgreements());

    add(tabs);
    tabs.selectTab(0);

    final Account a = Gerrit.getUserAccount();
    if (a != null) {
      display(a);
    }
  }

  private void infoRow(final int row, final String name) {
    info.setText(row, 0, name);
    info.getCellFormatter().addStyleName(row, 0, "header");
  }

  @Override
  public Object getScreenCacheToken() {
    return this;// Link.SETTINGS;
  }

  @Override
  public void onLoad() {
    super.onLoad();
    Util.ACCOUNT_SVC.myAccount(new GerritCallback<Account>() {
      public void onSuccess(final Account result) {
        if (isAttached()) {
          display(result);
        }
      }
    });
  }

  void display(final Account account) {
    info.setText(0, 1, account.getFullName());
    info.setText(1, 1, account.getPreferredEmail());
    info.setText(2, 1, FormatUtil.mediumFormat(account.getRegisteredOn()));
  }
}
