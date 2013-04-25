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

import com.google.gerrit.client.AvatarImage;
import com.google.gerrit.client.FormatUtil;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.NativeString;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gwt.i18n.client.LocaleInfo;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.VerticalPanel;

public class MyProfileScreen extends SettingsScreen {
  private AvatarImage avatar;
  private Anchor changeAvatar;
  private int labelIdx, fieldIdx;
  private Grid info;

  @Override
  protected void onInitUI() {
    super.onInitUI();

    HorizontalPanel h = new HorizontalPanel();
    add(h);

    VerticalPanel v = new VerticalPanel();
    v.addStyleName(Gerrit.RESOURCES.css().avatarInfoPanel());
    h.add(v);
    avatar = new AvatarImage();
    v.add(avatar);
    changeAvatar = new Anchor(Util.C.changeAvatar(), "", "_blank");
    changeAvatar.setVisible(false);
    v.add(changeAvatar);

    if (LocaleInfo.getCurrentLocale().isRTL()) {
      labelIdx = 1;
      fieldIdx = 0;
    } else {
      labelIdx = 0;
      fieldIdx = 1;
    }

    info = new Grid((Gerrit.getConfig().siteHasUsernames() ? 1 : 0) + 4, 2);
    info.setStyleName(Gerrit.RESOURCES.css().infoBlock());
    info.addStyleName(Gerrit.RESOURCES.css().accountInfoBlock());
    h.add(info);

    int row = 0;
    if (Gerrit.getConfig().siteHasUsernames()) {
      infoRow(row++, Util.C.userName());
    }
    infoRow(row++, Util.C.fullName());
    infoRow(row++, Util.C.preferredEmail());
    infoRow(row++, Util.C.registeredOn());
    infoRow(row++, Util.C.accountId());

    final CellFormatter fmt = info.getCellFormatter();
    fmt.addStyleName(0, 0, Gerrit.RESOURCES.css().topmost());
    fmt.addStyleName(0, 1, Gerrit.RESOURCES.css().topmost());
    fmt.addStyleName(row - 1, 0, Gerrit.RESOURCES.css().bottomheader());
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    display(Gerrit.getUserAccount());
    display();
  }

  private void infoRow(final int row, final String name) {
    info.setText(row, labelIdx, name);
    info.getCellFormatter().addStyleName(row, 0,
        Gerrit.RESOURCES.css().header());
  }

  void display(final Account account) {
    avatar.setAccount(FormatUtil.asInfo(account), 93, false);
    new RestApi("/accounts/").id("self").view("avatar.change.url")
        .get(new GerritCallback<NativeString>() {
          @Override
          public void onSuccess(NativeString changeUrl) {
            changeAvatar.setHref(changeUrl.asString());
            changeAvatar.setVisible(true);
          }
        });

    int row = 0;
    if (Gerrit.getConfig().siteHasUsernames()) {
      info.setWidget(row++, fieldIdx, new UsernameField());
    }
    info.setText(row++, fieldIdx, account.getFullName());
    info.setText(row++, fieldIdx, account.getPreferredEmail());
    info.setText(row++, fieldIdx, mediumFormat(account.getRegisteredOn()));
    info.setText(row++, fieldIdx, account.getId().toString());
  }
}
