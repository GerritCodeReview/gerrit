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

package com.google.gerrit.client.account;

import static com.google.gerrit.client.FormatUtil.mediumFormat;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.ui.AccountScreen;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.reviewdb.Account;
import com.google.gwt.event.logical.shared.SelectionEvent;
import com.google.gwt.event.logical.shared.SelectionHandler;
import com.google.gwt.i18n.client.LocaleInfo;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.LazyPanel;
import com.google.gwt.user.client.ui.TabPanel;
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;

import java.util.ArrayList;
import java.util.List;

public class AccountSettings extends AccountScreen {
  private final String initialTabToken;
  private int labelIdx, fieldIdx;
  private Grid info;

  private List<String> tabTokens;
  private TabPanel tabs;

  public AccountSettings(final String tabToken) {
    initialTabToken = tabToken;
  }

  @Override
  public boolean displayToken(String token) {
    final int tabIdx = tabTokens.indexOf(token);
    if (0 <= tabIdx) {
      tabs.selectTab(tabIdx);
      setToken(token);
      return true;
    } else {
      return false;
    }
  }

  @Override
  protected void onLoad() {
    super.onLoad();

    final int idx = tabTokens.indexOf(initialTabToken);
    tabs.selectTab(0 <= idx ? idx : 0);
    display(Gerrit.getUserAccount());
    display();
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();
    setPageTitle(Util.C.accountSettingsHeading());

    if (LocaleInfo.getCurrentLocale().isRTL()) {
      labelIdx = 1;
      fieldIdx = 0;
    } else {
      labelIdx = 0;
      fieldIdx = 1;
    }

    info = new Grid(4, 2);
    info.setStyleName(Gerrit.RESOURCES.css().infoBlock());
    info.addStyleName(Gerrit.RESOURCES.css().accountInfoBlock());
    add(info);

    infoRow(0, Util.C.fullName());
    infoRow(1, Util.C.preferredEmail());
    infoRow(2, Util.C.registeredOn());
    infoRow(3, Util.C.accountId());

    final CellFormatter fmt = info.getCellFormatter();
    fmt.addStyleName(0, 0, Gerrit.RESOURCES.css().topmost());
    fmt.addStyleName(0, 1, Gerrit.RESOURCES.css().topmost());
    fmt.addStyleName(3, 0, Gerrit.RESOURCES.css().bottomheader());

    tabTokens = new ArrayList<String>();
    tabs = new TabPanel();
    tabs.setWidth("98%");
    add(tabs);

    tabs.add(new LazyPanel() {
      @Override
      protected PreferencePanel createWidget() {
        return new PreferencePanel();
      }
    }, Util.C.tabPreferences());
    tabTokens.add(PageLinks.SETTINGS);

    tabs.add(new LazyPanel() {
      @Override
      protected ProjectWatchPanel createWidget() {
        return new ProjectWatchPanel();
      }
    }, Util.C.watchedProjects());
    tabTokens.add(PageLinks.SETTINGS_PROJECTS);

    tabs.add(new LazyPanel() {
      @Override
      protected ContactPanelFull createWidget() {
        final ContactPanelFull p = new ContactPanelFull();
        p.accountSettings = AccountSettings.this;
        return p;
      }
    }, Util.C.tabContactInformation());
    tabTokens.add(PageLinks.SETTINGS_CONTACT);

    if (Gerrit.getConfig().getSshdAddress() != null) {
      tabs.add(new LazyPanel() {
        @Override
        protected SshPanel createWidget() {
          return new SshPanel();
        }
      }, Util.C.tabSshKeys());
      tabTokens.add(PageLinks.SETTINGS_SSHKEYS);
    }

    tabs.add(new LazyPanel() {
      @Override
      protected ExternalIdPanel createWidget() {
        return new ExternalIdPanel();
      }
    }, Util.C.tabWebIdentities());
    tabTokens.add(PageLinks.SETTINGS_WEBIDENT);

    tabs.add(new LazyPanel() {
      @Override
      protected MyGroupsPanel createWidget() {
        return new MyGroupsPanel();
      }
    }, Util.C.tabMyGroups());
    tabTokens.add(PageLinks.SETTINGS_MYGROUPS);

    if (Gerrit.getConfig().isUseContributorAgreements()) {
      tabs.add(new LazyPanel() {
        @Override
        protected AgreementPanel createWidget() {
          return new AgreementPanel();
        }
      }, Util.C.tabAgreements());
      tabTokens.add(PageLinks.SETTINGS_AGREEMENTS);
    }

    tabs.addSelectionHandler(new SelectionHandler<Integer>() {
      @Override
      public void onSelection(final SelectionEvent<Integer> event) {
        setToken(tabTokens.get(event.getSelectedItem()));
      }
    });
  }

  private void infoRow(final int row, final String name) {
    info.setText(row, labelIdx, name);
    info.getCellFormatter().addStyleName(row, 0,
        Gerrit.RESOURCES.css().header());
  }

  void display(final Account account) {
    info.setText(0, fieldIdx, account.getFullName());
    info.setText(1, fieldIdx, account.getPreferredEmail());
    info.setText(2, fieldIdx, mediumFormat(account.getRegisteredOn()));
    info.setText(3, fieldIdx, account.getId().toString());
  }
}
