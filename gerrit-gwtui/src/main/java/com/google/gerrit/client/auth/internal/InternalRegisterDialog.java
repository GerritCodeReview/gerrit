// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.client.auth.internal;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.SignInDialog;
import com.google.gerrit.client.auth.userpass.UserPassResources;
import com.google.gerrit.common.auth.SignInMode;
import com.google.gerrit.common.auth.internal.RegistrationResult;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.FormPanel;
import com.google.gwt.user.client.ui.FormPanel.SubmitEvent;
import com.google.gwt.user.client.ui.FormSubmitCompleteEvent;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.PasswordTextBox;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwtexpui.globalkey.client.GlobalKey;
import com.google.gwtexpui.globalkey.client.NpTextBox;

public class InternalRegisterDialog extends SignInDialog implements
    FormPanel.SubmitHandler {
  static {
    UserPassResources.I.css().ensureInjected();
  }

  private final FlowPanel panelWidget;
  private final FormPanel form;
  private final FlowPanel formBody;
  private final FlowPanel redirectBody;

  private FlowPanel errorLine;
  private HTML errorMsg;

  private Button login;
  private Button close;
  private TextBox username;
  private PasswordTextBox password;

  public InternalRegisterDialog(final SignInMode requestedMode, final String token,
      final String initialErrorMsg) {
    super(requestedMode, token);

    formBody = new FlowPanel();
    formBody.setStyleName(UserPassResources.I.css().loginForm());

    form = new FormPanel();
    form.setMethod(FormPanel.METHOD_GET);
    form.addSubmitHandler(this);
    form.add(formBody);

    redirectBody = new FlowPanel();
    redirectBody.setVisible(false);

    panelWidget = new FlowPanel();
    panelWidget.add(form);
    panelWidget.add(redirectBody);
    add(panelWidget);

    createErrorBox();
    createUsernameBox();
    if (initialErrorMsg != null) {
      showError(initialErrorMsg);
    }
  }

  private void createErrorBox() {
    errorLine = new FlowPanel();
    DOM.setStyleAttribute(errorLine.getElement(), "visibility", "hidden");
    errorLine.setStyleName(UserPassResources.I.css().error());

    errorMsg = new HTML();
    errorLine.add(errorMsg);
    formBody.add(errorLine);
  }

  private void showError(final String msgText) {
    errorMsg.setHTML(errorMsg.getHTML() + "<br/>" + msgText);
    DOM.setStyleAttribute(errorLine.getElement(), "visibility", "");
  }

  private void hideError() {
    errorMsg.setText("");
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
    final PasswordTextBox verifyPassword = new PasswordTextBox();
    username.addKeyPressHandler(new KeyPressHandler() {
      @Override
      public void onKeyPress(final KeyPressEvent event) {
        if (event.getNativeEvent().getKeyCode() == KeyCodes.KEY_ENTER) {
          event.preventDefault();
          verifyPassword.selectAll();
          verifyPassword.setFocus(true);
        }
      }
    });
    verifyPassword.setVisibleLength(25);
    verifyPassword.addKeyPressHandler(GlobalKey.STOP_PROPAGATION);
    verifyPassword.addKeyPressHandler(new KeyPressHandler() {
      @Override
      public void onKeyPress(final KeyPressEvent event) {
        if (event.getNativeEvent().getKeyCode() == KeyCodes.KEY_ENTER) {
          event.preventDefault();
        }
      }
    });

    final FlowPanel buttons = new FlowPanel();
    buttons.setStyleName(Gerrit.RESOURCES.css().errorDialogButtons());

    login = new Button();
    login.setText(InternalUtil.C.buttonRegister());
    login.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        if (isInputValid(verifyPassword.getValue())) {
          form.submit();
        }
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

    final Grid formGrid = new Grid(4, 2);
    formGrid.setText(0, 0, InternalUtil.C.username());
    formGrid.setText(1, 0, InternalUtil.C.password());
    formGrid.setText(2, 0, InternalUtil.C.verifyPassword());
    formGrid.setWidget(0, 1, username);
    formGrid.setWidget(1, 1, password);
    formGrid.setWidget(2, 1, verifyPassword);
    formGrid.setWidget(3, 1, buttons);
    formBody.add(formGrid);

    username.setTabIndex(1);
    password.setTabIndex(2);
    verifyPassword.setTabIndex(3);
    login.setTabIndex(5);
    close.setTabIndex(6);
  }

  private void enable(final boolean on) {
    login.setEnabled(on);
  }

  private boolean isInputValid(String verifiedPassword) {
    hideError();
    boolean isFormValid = true;
    String login = username.getValue();
    if (login.trim().isEmpty()) {
      showError(InternalUtil.C.usernameRequired());
      isFormValid = false;
    } else if (!login.matches("^[a-zA-Z].*$")) {
      showError(InternalUtil.C.invalidUsername());
      isFormValid = false;
    }
    String pass = password.getText();
    if (pass.trim().isEmpty()) {
      showError(InternalUtil.C.passwordRequired());
      isFormValid = false;
    } else if (verifiedPassword.isEmpty()) {
      showError(InternalUtil.C.verifyPasswordRequired());
      isFormValid = false;
    } else if (!pass.equals(verifiedPassword)) {
      showError(InternalUtil.C.passwordsDontMatch());
      isFormValid = false;
    }

    return isFormValid;
  }

  @Override
  public void onSubmit(final SubmitEvent event) {
    event.cancel();
    enable(false);
    hideError();
    InternalUtil.IRS.register(username.getValue(), password.getValue(),
        new AsyncCallback<RegistrationResult>() {

      @Override
      public void onSuccess(RegistrationResult result) {
        if (result.success) {
          onRegistrationSuccess();
        } else if (result.alreadyExist) {
          showError(InternalUtil.C.accountAlreadyExist());
          enable(true);
        }
      }

      @Override
      public void onFailure(Throwable caught) {
        showError(InternalUtil.C.generalFailure());
      }
    });
  }

  public void onSubmitComplete(final FormSubmitCompleteEvent event) {
  }

  private void onRegistrationSuccess() {
    login.setVisible(false);
    formBody.clear();
    final Grid formGrid = new Grid(2, 1);
    formGrid.setText(0, 0, InternalUtil.C.registrationSuccess());
    formGrid.setWidget(1, 0, close);
    formBody.add(formGrid);
  }

}
