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

import static com.google.gerrit.reviewdb.AccountExternalId.SCHEME_USERNAME;

import com.google.gerrit.client.ErrorDialog;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.TextSaveButtonListener;
import com.google.gerrit.common.errors.InvalidUserNameException;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountExternalId;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.i18n.client.LocaleInfo;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;
import com.google.gwtexpui.clippy.client.CopyableLabel;
import com.google.gwtexpui.globalkey.client.NpTextBox;
import com.google.gwtjsonrpc.client.VoidResult;

import java.util.List;

public class UsernamePanel extends Composite {
  private NpTextBox userNameTxt;
  private Button changeUserName;

  private CopyableLabel password;
  private Button generatePassword;

  private AccountExternalId.Key idKey;

  UsernamePanel() {
    final FlowPanel body = new FlowPanel();
    initWidget(body);

    userNameTxt = new NpTextBox();
    userNameTxt.addKeyPressHandler(new UserNameValidator());
    userNameTxt.addStyleName(Gerrit.RESOURCES.css().sshPanelUsername());
    userNameTxt.setVisibleLength(16);
    userNameTxt.setReadOnly(!canEditUserName());

    changeUserName = new Button(Util.C.buttonChangeUserName());
    changeUserName.setVisible(canEditUserName());
    changeUserName.setEnabled(false);
    changeUserName.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        doChangeUserName();
      }
    });
    new TextSaveButtonListener(userNameTxt, changeUserName);

    password = new CopyableLabel("");
    password.addStyleName(Gerrit.RESOURCES.css().sshPanelPassword());
    password.setVisible(false);

    generatePassword = new Button(Util.C.buttonGeneratePassword());
    generatePassword.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        doGeneratePassword();
      }
    });

    final Grid userInfo = new Grid(2, 3);
    final CellFormatter fmt = userInfo.getCellFormatter();
    userInfo.setStyleName(Gerrit.RESOURCES.css().infoBlock());
    userInfo.addStyleName(Gerrit.RESOURCES.css().accountInfoBlock());
    body.add(userInfo);

    row(userInfo, 0, Util.C.userName(), userNameTxt, changeUserName);
    row(userInfo, 1, Util.C.password(), password, generatePassword);

    fmt.addStyleName(0, 0, Gerrit.RESOURCES.css().topmost());
    fmt.addStyleName(0, 1, Gerrit.RESOURCES.css().topmost());
    fmt.addStyleName(0, 2, Gerrit.RESOURCES.css().topmost());

    fmt.addStyleName(1, 0, Gerrit.RESOURCES.css().bottomheader());
  }

  @Override
  protected void onLoad() {
    super.onLoad();

    enableUI(false);
    Util.ACCOUNT_SEC
        .myExternalIds(new GerritCallback<List<AccountExternalId>>() {
          public void onSuccess(final List<AccountExternalId> result) {
            AccountExternalId id = null;
            for (AccountExternalId i : result) {
              if (i.isScheme(SCHEME_USERNAME)) {
                id = i;
                break;
              }
            }
            display(id);
          }
        });
  }

  private void display(AccountExternalId id) {
    String user, pass;
    if (id != null) {
      idKey = id.getKey();
      user = id.getSchemeRest();
      pass = id.getPassword();
    } else {
      idKey = null;
      user = null;
      pass = null;
    }

    Gerrit.getUserAccount().setUserName(user);
    userNameTxt.setText(user);
    userNameTxt.setEnabled(true);
    generatePassword.setEnabled(idKey != null);

    if (pass != null) {
      password.setText(pass);
      password.setVisible(true);
    } else {
      password.setVisible(false);
    }
  }

  private void row(final Grid info, final int row, final String name,
      final Widget field1, final Widget field2) {
    final CellFormatter fmt = info.getCellFormatter();
    if (LocaleInfo.getCurrentLocale().isRTL()) {
      info.setText(row, 2, name);
      info.setWidget(row, 1, field1);
      info.setWidget(row, 0, field2);
      fmt.addStyleName(row, 1, Gerrit.RESOURCES.css().noborder());
      fmt.addStyleName(row, 2, Gerrit.RESOURCES.css().header());
    } else {
      info.setText(row, 0, name);
      info.setWidget(row, 1, field1);
      info.setWidget(row, 2, field2);
      fmt.addStyleName(row, 1, Gerrit.RESOURCES.css().noborder());
      fmt.addStyleName(row, 0, Gerrit.RESOURCES.css().header());
    }
  }

  private boolean canEditUserName() {
    return Gerrit.getConfig().canEdit(Account.FieldName.USER_NAME);
  }

  void doChangeUserName() {
    if (!canEditUserName()) {
      return;
    }

    String newName = userNameTxt.getText();
    if ("".equals(newName)) {
      newName = null;
    }
    if (newName != null && !newName.matches(Account.USER_NAME_PATTERN)) {
      invalidUserName();
      return;
    }

    enableUI(false);

    final String newUserName = newName;
    Util.ACCOUNT_SEC.changeUserName(newUserName,
        new GerritCallback<VoidResult>() {
          public void onSuccess(final VoidResult result) {
            Gerrit.getUserAccount().setUserName(newUserName);
            enableUI(true);
          }

          @Override
          public void onFailure(final Throwable caught) {
            enableUI(true);
            if (InvalidUserNameException.MESSAGE.equals(caught.getMessage())) {
              invalidUserName();
            } else {
              super.onFailure(caught);
            }
          }
        });
  }

  void invalidUserName() {
    userNameTxt.setFocus(true);
    new ErrorDialog(Util.C.invalidUserName()).center();
  }

  void doGeneratePassword() {
    if (idKey == null) {
      return;
    }

    enableUI(false);

    Util.ACCOUNT_SEC.generatePassword(idKey,
        new GerritCallback<AccountExternalId>() {
          public void onSuccess(final AccountExternalId result) {
            enableUI(true);
            display(result);
          }

          @Override
          public void onFailure(final Throwable caught) {
            enableUI(true);
            if (InvalidUserNameException.MESSAGE.equals(caught.getMessage())) {
              invalidUserName();
            } else {
              super.onFailure(caught);
            }
          }
        });
  }

  private void enableUI(final boolean on) {
    userNameTxt.setEnabled(on);
    changeUserName.setEnabled(on);
    generatePassword.setEnabled(on && idKey != null);
  }

  private final class UserNameValidator implements KeyPressHandler {
    @Override
    public void onKeyPress(final KeyPressEvent event) {
      final char code = event.getCharCode();
      switch (code) {
        case KeyCodes.KEY_ALT:
        case KeyCodes.KEY_BACKSPACE:
        case KeyCodes.KEY_CTRL:
        case KeyCodes.KEY_DELETE:
        case KeyCodes.KEY_DOWN:
        case KeyCodes.KEY_END:
        case KeyCodes.KEY_ENTER:
        case KeyCodes.KEY_ESCAPE:
        case KeyCodes.KEY_HOME:
        case KeyCodes.KEY_LEFT:
        case KeyCodes.KEY_PAGEDOWN:
        case KeyCodes.KEY_PAGEUP:
        case KeyCodes.KEY_RIGHT:
        case KeyCodes.KEY_SHIFT:
        case KeyCodes.KEY_TAB:
        case KeyCodes.KEY_UP:
          // Allow these, even if one of their assigned codes is
          // identical to an ASCII character we do not want to
          // allow in the box.
          //
          // We still want to let the user move around the input box
          // with their arrow keys, or to move between fields using tab.
          // Invalid characters introduced will be caught through the
          // server's own validation of the input data.
          //
          break;

        default:
          final TextBox box = (TextBox) event.getSource();
          final String re;
          if (box.getCursorPos() == 0)
            re = Account.USER_NAME_PATTERN_FIRST;
          else
            re = Account.USER_NAME_PATTERN_REST;
          if (!String.valueOf(code).matches("^" + re + "$")) {
            event.preventDefault();
            event.stopPropagation();
          }
      }
    }
  }
}
