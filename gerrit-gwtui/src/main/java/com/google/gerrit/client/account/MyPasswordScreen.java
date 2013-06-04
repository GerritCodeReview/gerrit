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

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.VoidResult;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.NativeString;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.i18n.client.LocaleInfo;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtexpui.clippy.client.CopyableLabel;

public class MyPasswordScreen extends SettingsScreen {
  private CopyableLabel password;
  private Button generatePassword;
  private Button clearPassword;

  @Override
  protected void onInitUI() {
    super.onInitUI();

    String url = Gerrit.getConfig().getHttpPasswordUrl();
    if (url != null) {
      Anchor link = new Anchor();
      link.setText(Util.C.linkObtainPassword());
      link.setHref(url);
      link.setTarget("_blank");
      add(link);
      return;
    }

    password = new CopyableLabel("");
    password.addStyleName(Gerrit.RESOURCES.css().accountPassword());

    generatePassword = new Button(Util.C.buttonGeneratePassword());
    generatePassword.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        doGeneratePassword();
      }
    });

    clearPassword = new Button(Util.C.buttonClearPassword());
    clearPassword.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        doClearPassword();
      }
    });

    final Grid userInfo = new Grid(2, 2);
    final CellFormatter fmt = userInfo.getCellFormatter();
    userInfo.setStyleName(Gerrit.RESOURCES.css().infoBlock());
    userInfo.addStyleName(Gerrit.RESOURCES.css().accountInfoBlock());
    add(userInfo);

    row(userInfo, 0, Util.C.userName(), new UsernameField());
    row(userInfo, 1, Util.C.password(), password);

    fmt.addStyleName(0, 0, Gerrit.RESOURCES.css().topmost());
    fmt.addStyleName(0, 1, Gerrit.RESOURCES.css().topmost());
    fmt.addStyleName(1, 0, Gerrit.RESOURCES.css().bottomheader());

    final FlowPanel buttons = new FlowPanel();
    buttons.add(generatePassword);
    buttons.add(clearPassword);
    add(buttons);
  }

  @Override
  protected void onLoad() {
    super.onLoad();

    if (password == null) {
      display();
      return;
    }

    enableUI(false);
    AccountApi.getUsername("self", new GerritCallback<NativeString>() {
      @Override
      public void onSuccess(NativeString user) {
        Gerrit.getUserAccount().setUserName(user.asString());
        refreshHttpPassword();
      }

      @Override
      public void onFailure(final Throwable caught) {
        if (RestApi.isNotFound(caught)) {
          Gerrit.getUserAccount().setUserName(null);
          display();
        } else {
          super.onFailure(caught);
        }
      }
    });
  }

  private void refreshHttpPassword() {
    AccountApi.getHttpPassword("self", new ScreenLoadCallback<NativeString>(
        this) {
      @Override
      protected void preDisplay(NativeString httpPassword) {
        display(httpPassword.asString());
      }

      @Override
      public void onFailure(final Throwable caught) {
        if (RestApi.isNotFound(caught)) {
          display(null);
          display();
        } else {
          super.onFailure(caught);
        }
      }
    });
  }

  private void display(String pass) {
    password.setText(pass != null ? pass : "");
    password.setVisible(pass != null);
    enableUI(true);
  }

  private void row(final Grid info, final int row, final String name,
      final Widget field) {
    final CellFormatter fmt = info.getCellFormatter();
    if (LocaleInfo.getCurrentLocale().isRTL()) {
      info.setText(row, 1, name);
      info.setWidget(row, 0, field);
      fmt.addStyleName(row, 1, Gerrit.RESOURCES.css().header());
    } else {
      info.setText(row, 0, name);
      info.setWidget(row, 1, field);
      fmt.addStyleName(row, 0, Gerrit.RESOURCES.css().header());
    }
  }

  private void doGeneratePassword() {
    if (Gerrit.getUserAccount().getUserName() != null) {
      enableUI(false);
      AccountApi.generateHttpPassword("self",
          new GerritCallback<NativeString>() {
            @Override
            public void onSuccess(NativeString newPassword) {
              display(newPassword.asString());
            }

            @Override
            public void onFailure(final Throwable caught) {
              enableUI(true);
            }
          });
    }
  }

  private void doClearPassword() {
    if (Gerrit.getUserAccount().getUserName() != null) {
      enableUI(false);
      AccountApi.clearHttpPassword("self",
          new GerritCallback<VoidResult>() {
            @Override
            public void onSuccess(VoidResult result) {
              display(null);
            }

            @Override
            public void onFailure(final Throwable caught) {
              enableUI(true);
            }
          });
    }
  }

  private void enableUI(boolean on) {
    on &= Gerrit.getUserAccount().getUserName() != null;

    generatePassword.setEnabled(on);
    clearPassword.setVisible(on && !"".equals(password.getText()));
  }
}
