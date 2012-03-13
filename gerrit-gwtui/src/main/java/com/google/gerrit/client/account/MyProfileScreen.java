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
import com.google.gerrit.reviewdb.client.Account;
import com.google.gwt.i18n.client.LocaleInfo;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;

public class MyProfileScreen extends SettingsScreen {
  protected int labelIdx, fieldIdx;
  protected Grid info;

  @Override
  protected void onInitUI() {
    super.onInitUI();

    if (LocaleInfo.getCurrentLocale().isRTL()) {
      labelIdx = 1;
      fieldIdx = 0;
    } else {
      labelIdx = 0;
      fieldIdx = 1;
    }

    info = createGrid();
    info.setStyleName(Gerrit.RESOURCES.css().infoBlock());
    info.addStyleName(Gerrit.RESOURCES.css().accountInfoBlock());
    add(info);

    infoRow(0, Util.C.userName());
    infoRow(1, Util.C.fullName());
    infoRow(2, Util.C.preferredEmail());
    infoRow(3, Util.C.registeredOn());
    infoRow(4, Util.C.accountId());

    final CellFormatter fmt = info.getCellFormatter();
    fmt.addStyleName(0, 0, Gerrit.RESOURCES.css().topmost());
    fmt.addStyleName(0, 1, Gerrit.RESOURCES.css().topmost());
    fmt.addStyleName(4, 0, Gerrit.RESOURCES.css().bottomheader());
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    display(Gerrit.getUserAccount());
    display();
  }

  protected Grid createGrid() {
    return new Grid(5, 2);
  }

  protected void infoRow(final int row, final String name) {
    info.setText(row, labelIdx, name);
    info.getCellFormatter().addStyleName(row, 0,
        Gerrit.RESOURCES.css().header());
  }

  void display(final Account account) {
    info.setWidget(0, fieldIdx, new UsernameField());
    info.setText(1, fieldIdx, account.getFullName());
    info.setText(2, fieldIdx, account.getPreferredEmail());
    info.setText(3, fieldIdx, mediumFormat(account.getRegisteredOn()));
    info.setText(4, fieldIdx, account.getId().toString());
  }
}
