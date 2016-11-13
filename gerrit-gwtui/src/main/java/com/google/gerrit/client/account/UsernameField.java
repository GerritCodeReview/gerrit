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

import com.google.gerrit.client.ConfirmationCallback;
import com.google.gerrit.client.ConfirmationDialog;
import com.google.gerrit.client.ErrorDialog;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.NativeString;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gerrit.client.ui.OnEditEnabler;
import com.google.gerrit.extensions.client.AccountFieldName;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwtexpui.clippy.client.CopyableLabel;
import com.google.gwtexpui.globalkey.client.NpTextBox;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;

class UsernameField extends Composite {
  private CopyableLabel userNameLbl;
  private NpTextBox userNameTxt;
  private Button setUserName;

  UsernameField() {
    String user = Gerrit.getUserAccount().username();
    userNameLbl = new CopyableLabel(user != null ? user : "");
    userNameLbl.setStyleName(Gerrit.RESOURCES.css().accountUsername());

    if (user != null || !canEditUserName()) {
      initWidget(userNameLbl);

    } else {
      final FlowPanel body = new FlowPanel();
      initWidget(body);
      setStyleName(Gerrit.RESOURCES.css().usernameField());

      userNameTxt = new NpTextBox();
      userNameTxt.addKeyPressHandler(new UserNameValidator());
      userNameTxt.addStyleName(Gerrit.RESOURCES.css().accountUsername());
      userNameTxt.setVisibleLength(16);
      userNameTxt.addKeyPressHandler(
          new KeyPressHandler() {
            @Override
            public void onKeyPress(KeyPressEvent event) {
              if (event.getNativeEvent().getKeyCode() == KeyCodes.KEY_ENTER) {
                confirmSetUserName();
              }
            }
          });

      setUserName = new Button(Util.C.buttonSetUserName());
      setUserName.setVisible(canEditUserName());
      setUserName.setEnabled(false);
      setUserName.addClickHandler(
          new ClickHandler() {
            @Override
            public void onClick(final ClickEvent event) {
              confirmSetUserName();
            }
          });
      new OnEditEnabler(setUserName, userNameTxt);

      userNameLbl.setVisible(false);
      body.add(userNameLbl);
      body.add(userNameTxt);
      body.add(setUserName);
    }
  }

  private boolean canEditUserName() {
    return Gerrit.info().auth().canEdit(AccountFieldName.USER_NAME);
  }

  private void confirmSetUserName() {
    new ConfirmationDialog(
            Util.C.confirmSetUserNameTitle(),
            new SafeHtmlBuilder().append(Util.C.confirmSetUserName()),
            new ConfirmationCallback() {
              @Override
              public void onOk() {
                doSetUserName();
              }
            })
        .center();
  }

  private void doSetUserName() {
    if (!canEditUserName()) {
      return;
    }

    enableUI(false);

    String newName = userNameTxt.getText();
    if ("".equals(newName)) {
      newName = null;
    }
    final String newUserName = newName;

    AccountApi.setUsername(
        "self",
        newUserName,
        new GerritCallback<NativeString>() {
          @Override
          public void onSuccess(NativeString result) {
            Gerrit.getUserAccount().username(newUserName);
            userNameLbl.setText(newUserName);
            userNameLbl.setVisible(true);
            userNameTxt.setVisible(false);
            setUserName.setVisible(false);
          }

          @Override
          public void onFailure(Throwable caught) {
            enableUI(true);
            if (RestApi.isExpected(422 /* Unprocessable Entity */)) {
              new ErrorDialog(Util.C.invalidUserName()).center();
            } else {
              super.onFailure(caught);
            }
          }
        });
  }

  private void enableUI(final boolean on) {
    userNameTxt.setEnabled(on);
    setUserName.setEnabled(on);
  }

  private static final class UserNameValidator implements KeyPressHandler {
    @Override
    public void onKeyPress(final KeyPressEvent event) {
      final char code = event.getCharCode();
      final int nativeCode = event.getNativeEvent().getKeyCode();
      switch (nativeCode) {
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
          if (box.getCursorPos() == 0) {
            re = Account.USER_NAME_PATTERN_FIRST;
          } else {
            re = Account.USER_NAME_PATTERN_REST;
          }
          if (!String.valueOf(code).matches("^" + re + "$")) {
            event.preventDefault();
            event.stopPropagation();
          }
      }
    }
  }
}
