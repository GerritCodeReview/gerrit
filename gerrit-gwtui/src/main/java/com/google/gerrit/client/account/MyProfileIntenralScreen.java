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
// limitations under the License

package com.google.gerrit.client.account;

import com.google.gerrit.client.ErrorDialog;
import com.google.gerrit.common.auth.internal.PasswordChangeResult;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.PasswordTextBox;

public class MyProfileIntenralScreen extends MyProfileScreen {
  private PasswordTextBox currentPassword;
  private PasswordTextBox newPassword;
  private PasswordTextBox repeatedPassword;
  private Button save;

  @Override
  protected void onInitUI() {
    super.onInitUI();

    infoRow(5, Util.C.passwordChange());
    infoRow(6, Util.C.currentPassword());
    infoRow(7, Util.C.newPassword());
    infoRow(8, Util.C.repeatPassword());

    currentPassword = new PasswordTextBox();
    newPassword = new PasswordTextBox();
    repeatedPassword = new PasswordTextBox();
    save = new Button();
    save.setText(Util.C.saveChanges());
    save.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        onSaveAction();
      }

    });
  }

  @Override
  protected Grid createGrid() {
    return new Grid(10, 2);
  }

  void display(final Account account) {
    super.display(account);
    info.setWidget(6, fieldIdx, currentPassword);
    info.setWidget(7, fieldIdx, newPassword);
    info.setWidget(8, fieldIdx, repeatedPassword);
    info.setWidget(9, fieldIdx, save);
  }

  private void onSaveAction() {
    if (verifyNewPasswords()) {
      save.setEnabled(false);
      Util.INTERNAL_PASSWORD_CHANGE_SVC.changePassword(
          currentPassword.getText(), newPassword.getText(),
          new AsyncCallback<PasswordChangeResult>() {
            @Override
            public void onSuccess(PasswordChangeResult result) {
              save.setEnabled(true);
              if (result.success) {
                Window.alert(Util.C.passwordChangedSuccessfully());
                currentPassword.setText("");
                newPassword.setText("");
                repeatedPassword.setText("");
              } else {
                new ErrorDialog(Util.C.invalidCurrentPasswordError()).center();
              }
            }
            @Override
            public void onFailure(Throwable caught) {
              save.setEnabled(true);
              new ErrorDialog(caught).center();
            }
          });
    }
  }

  private boolean verifyNewPasswords() {
    String newPasswordText = newPassword.getText();
    String repeatedPasswordText = repeatedPassword.getText();
    if (newPasswordText.isEmpty() || repeatedPasswordText.isEmpty()) {
      new ErrorDialog(Util.C.emptyPasswordError()).center();
      return false;
    }
    if (!newPasswordText.equals(repeatedPasswordText)) {
      new ErrorDialog(Util.C.passwordsDoesntMatchError()).center();
      return false;
    }
    return true;
  }
}
