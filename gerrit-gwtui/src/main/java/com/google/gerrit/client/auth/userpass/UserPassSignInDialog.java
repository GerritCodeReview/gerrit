// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.client.auth.userpass;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.SignInDialog;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.SmallHeading;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.common.auth.SignInMode;
import com.google.gerrit.common.auth.userpass.LoginResult;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.DeferredCommand;
import com.google.gwt.user.client.Window.Location;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.PasswordTextBox;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwtexpui.globalkey.client.GlobalKey;
import com.google.gwtexpui.globalkey.client.NpTextBox;

public class UserPassSignInDialog extends SignInDialog {
  static {
    UserPassResources.I.css().ensureInjected();
  }

  private final FlowPanel formBody;

  private FlowPanel errorLine;
  private InlineLabel errorMsg;

  private Button login;
  private Button close;
  private TextBox username;
  private TextBox password;

  public UserPassSignInDialog(final String token, final String initialErrorMsg) {
    super(SignInMode.SIGN_IN, token);
    setAutoHideEnabled(false);

    formBody = new FlowPanel();
    formBody.setStyleName(UserPassResources.I.css().loginForm());
    add(formBody);

    createHeaderText();
    createErrorBox();
    createUsernameBox();
    if (initialErrorMsg != null) {
      showError(initialErrorMsg);
    }
  }

  @Override
  public void show() {
    super.show();
    DeferredCommand.addCommand(new Command() {
      @Override
      public void execute() {
        username.setFocus(true);
      }
    });
  }

  private void createHeaderText() {
    final FlowPanel headerText = new FlowPanel();
    final SmallHeading headerLabel = new SmallHeading();
    headerLabel.setText(Util.M.signInAt(Location.getHostName()));
    headerText.add(headerLabel);
    formBody.add(headerText);
  }

  private void createErrorBox() {
    errorLine = new FlowPanel();
    DOM.setStyleAttribute(errorLine.getElement(), "visibility", "hidden");
    errorLine.setStyleName(UserPassResources.I.css().error());

    errorMsg = new InlineLabel();
    errorLine.add(errorMsg);
    formBody.add(errorLine);
  }

  private void showError(final String msgText) {
    errorMsg.setText(msgText);
    DOM.setStyleAttribute(errorLine.getElement(), "visibility", "");
  }

  private void hideError() {
    DOM.setStyleAttribute(errorLine.getElement(), "visibility", "hidden");
  }

  private void createUsernameBox() {
    username = new NpTextBox();
    username.setVisibleLength(25);
    username.addKeyPressHandler(new KeyPressHandler() {
      @Override
      public void onKeyPress(final KeyPressEvent event) {
        if (event.getNativeEvent().getKeyCode() == KeyCodes.KEY_ENTER) {
          event.preventDefault();
          password.selectAll();
          password.setFocus(true);
        }
      }
    });

    password = new PasswordTextBox();
    password.setVisibleLength(25);
    password.addKeyPressHandler(GlobalKey.STOP_PROPAGATION);
    password.addKeyPressHandler(new KeyPressHandler() {
      @Override
      public void onKeyPress(final KeyPressEvent event) {
        if (event.getNativeEvent().getKeyCode() == KeyCodes.KEY_ENTER) {
          event.preventDefault();
          onLogin();
        }
      }
    });

    final FlowPanel buttons = new FlowPanel();
    buttons.setStyleName(Gerrit.RESOURCES.css().errorDialogButtons());

    login = new Button();
    login.setText(Util.C.buttonSignIn());
    login.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        onLogin();
      }
    });
    buttons.add(login);

    close = new Button();
    DOM.setStyleAttribute(close.getElement(), "marginLeft", "45px");
    close.setText(Gerrit.C.signInDialogClose());
    close.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        hide();
      }
    });
    buttons.add(close);

    final Grid formGrid = new Grid(3, 2);
    formGrid.setText(0, 0, Util.C.username());
    formGrid.setText(1, 0, Util.C.password());
    formGrid.setWidget(0, 1, username);
    formGrid.setWidget(1, 1, password);
    formGrid.setWidget(2, 1, buttons);
    formBody.add(formGrid);

    username.setTabIndex(1);
    password.setTabIndex(2);
    login.setTabIndex(3);
    close.setTabIndex(4);
  }

  private void enable(final boolean on) {
    username.setEnabled(on);
    password.setEnabled(on);
    login.setEnabled(on);
  }

  private void onLogin() {
    hideError();

    final String user = username.getText();
    if (user == null || user.equals("")) {
      showError(Util.C.usernameRequired());
      username.setFocus(true);
      return;
    }

    final String pass = password.getText();
    if (pass == null || pass.equals("")) {
      showError(Util.C.passwordRequired());
      password.setFocus(true);
      return;
    }

    enable(false);
    Util.SVC.authenticate(user, pass, new GerritCallback<LoginResult>() {
      public void onSuccess(final LoginResult result) {
        if (result.success) {
          String to = token;
          if (result.isNew && !to.startsWith(PageLinks.REGISTER + ",")) {
            to = PageLinks.REGISTER + "," + to;
          }

          // Unfortunately we no longer support updating the web UI when the
          // user signs in. Instead we must force a reload of the page, but
          // that isn't easy because we might need to change the anchor. So
          // we bounce through a little redirection servlet on the server.
          //
          Location.replace(Location.getPath() + "login/" + to);
        } else {
          showError(Util.C.invalidLogin());
          enable(true);
          password.selectAll();
          DeferredCommand.addCommand(new Command() {
            @Override
            public void execute() {
              password.setFocus(true);
            }
          });
        }
      }

      @Override
      public void onFailure(final Throwable caught) {
        super.onFailure(caught);
        enable(true);
      }
    });
  }
}
