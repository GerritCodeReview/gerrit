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
import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.AccountExternalId;
import com.google.gerrit.client.reviewdb.ContactInformation;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.TextSaveButtonListener;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.i18n.client.LocaleInfo;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.FormPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.user.client.ui.FormPanel.SubmitEvent;
import com.google.gwtexpui.globalkey.client.NpTextBox;
import com.google.gwtexpui.user.client.AutoCenterDialogBox;
import com.google.gwtjsonrpc.client.VoidResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class ContactPanelShort extends Composite {
  AccountSettings accountSettings;

  protected final FlowPanel body;
  protected int labelIdx, fieldIdx;
  protected Button save;

  private String currentEmail;
  protected boolean haveAccount;
  private boolean haveEmails;

  NpTextBox nameTxt;
  private ListBox emailPick;
  private Button registerNewEmail;

  ContactPanelShort() {
    body = new FlowPanel();
    initWidget(body);
  }

  protected void onInitUI() {
    if (LocaleInfo.getCurrentLocale().isRTL()) {
      labelIdx = 1;
      fieldIdx = 0;
    } else {
      labelIdx = 0;
      fieldIdx = 1;
    }

    nameTxt = new NpTextBox();
    nameTxt.setVisibleLength(60);

    emailPick = new ListBox();

    final Grid infoPlainText = new Grid(2, 2);
    infoPlainText.setStyleName("gerrit-InfoBlock");
    infoPlainText.addStyleName("gerrit-AccountInfoBlock");

    body.add(infoPlainText);

    registerNewEmail = new Button(Util.C.buttonOpenRegisterNewEmail());
    registerNewEmail.setEnabled(false);
    registerNewEmail.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        doRegisterNewEmail();
      }
    });
    final FlowPanel emailLine = new FlowPanel();
    emailLine.add(emailPick);
    if (Gerrit.getConfig().isAllowRegisterNewEmail()) {
      emailLine.add(registerNewEmail);
    }

    row(infoPlainText, 0, Util.C.contactFieldFullName(), nameTxt);
    row(infoPlainText, 1, Util.C.contactFieldEmail(), emailLine);

    infoPlainText.getCellFormatter().addStyleName(0, 0, "topmost");
    infoPlainText.getCellFormatter().addStyleName(0, 1, "topmost");
    infoPlainText.getCellFormatter().addStyleName(1, 0, "bottomheader");

    save = new Button(Util.C.buttonSaveChanges());
    save.setEnabled(false);
    save.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        doSave();
      }
    });

    final TextSaveButtonListener sbl = new TextSaveButtonListener(save);
    nameTxt.addKeyPressHandler(sbl);
    emailPick.addChangeHandler(new ChangeHandler() {
      @Override
      public void onChange(final ChangeEvent event) {
        final int idx = emailPick.getSelectedIndex();
        final String v = 0 <= idx ? emailPick.getValue(idx) : null;
        if (Util.C.buttonOpenRegisterNewEmail().equals(v)) {
          for (int i = 0; i < emailPick.getItemCount(); i++) {
            if (currentEmail.equals(emailPick.getValue(i))) {
              emailPick.setSelectedIndex(i);
              break;
            }
          }
          doRegisterNewEmail();
        } else {
          save.setEnabled(true);
        }
      }
    });
  }

  void hideSaveButton() {
    save.setVisible(false);
  }

  @Override
  protected void onLoad() {
    super.onLoad();

    onInitUI();
    body.add(save);
    display(Gerrit.getUserAccount());

    emailPick.clear();
    emailPick.setEnabled(false);
    registerNewEmail.setEnabled(false);

    haveAccount = false;
    haveEmails = false;

    Util.ACCOUNT_SVC.myAccount(new GerritCallback<Account>() {
      public void onSuccess(final Account result) {
        if (!isAttached()) {
          return;
        }
        display(result);
        haveAccount = true;
        postLoad();
      }
    });
    Util.ACCOUNT_SEC
        .myExternalIds(new GerritCallback<List<AccountExternalId>>() {
          public void onSuccess(final List<AccountExternalId> result) {
            if (!isAttached()) {
              return;
            }
            final Set<String> emails = new HashSet<String>();
            for (final AccountExternalId i : result) {
              if (i.getEmailAddress() != null
                  && i.getEmailAddress().length() > 0) {
                emails.add(i.getEmailAddress());
              }
            }
            final List<String> addrs = new ArrayList<String>(emails);
            Collections.sort(addrs);
            for (String s : addrs) {
              emailPick.addItem(s);
            }
            haveEmails = true;
            postLoad();
          }
        });
  }

  private void postLoad() {
    if (haveAccount && haveEmails) {
      if (currentEmail != null) {
        boolean found = false;
        for (int i = 0; i < emailPick.getItemCount(); i++) {
          if (currentEmail.equals(emailPick.getValue(i))) {
            emailPick.setSelectedIndex(i);
            found = true;
            break;
          }
        }
        if (!found) {
          emailPick.addItem(currentEmail);
          emailPick.setSelectedIndex(emailPick.getItemCount() - 1);
        }
      }
      if (emailPick.getItemCount() > 0) {
        emailPick.setVisible(true);
        emailPick.setEnabled(true);
        if (Gerrit.getConfig().isAllowRegisterNewEmail()) {
          final String t = Util.C.buttonOpenRegisterNewEmail();
          emailPick.addItem("... " + t + "  ", t);
        }
      } else {
        emailPick.setVisible(false);
      }
      registerNewEmail.setEnabled(true);
    }
  }

  protected void row(final Grid info, final int row, final String name,
      final Widget field) {
    info.setText(row, labelIdx, name);
    info.setWidget(row, fieldIdx, field);
    info.getCellFormatter().addStyleName(row, 0, "header");
  }

  protected void display(final Account userAccount) {
    currentEmail = userAccount.getPreferredEmail();
    nameTxt.setText(userAccount.getFullName());
    save.setEnabled(false);
  }

  private void doRegisterNewEmail() {
    if (!Gerrit.getConfig().isAllowRegisterNewEmail()) {
      return;
    }

    final AutoCenterDialogBox box = new AutoCenterDialogBox(true, true);
    final VerticalPanel body = new VerticalPanel();

    final NpTextBox inEmail = new NpTextBox();
    inEmail.setVisibleLength(60);

    final Button register = new Button(Util.C.buttonSendRegisterNewEmail());
    final FormPanel form = new FormPanel();
    form.addSubmitHandler(new FormPanel.SubmitHandler() {
      @Override
      public void onSubmit(final SubmitEvent event) {
        event.cancel();
        final String addr = inEmail.getText().trim();
        if (!addr.contains("@")) {
          return;
        }

        inEmail.setEnabled(false);
        register.setEnabled(false);
        Util.ACCOUNT_SEC.registerEmail(addr, new GerritCallback<VoidResult>() {
          public void onSuccess(VoidResult result) {
            box.hide();
          }

          @Override
          public void onFailure(final Throwable caught) {
            inEmail.setEnabled(true);
            register.setEnabled(true);
            super.onFailure(caught);
          }
        });
      }
    });
    form.setWidget(body);

    register.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        form.submit();
      }
    });
    body.add(new HTML(Util.C.descRegisterNewEmail()));
    body.add(inEmail);
    body.add(register);

    box.setText(Util.C.titleRegisterNewEmail());
    box.setWidget(form);
    box.center();
    inEmail.setFocus(true);
  }

  void doSave() {
    String newName = nameTxt.getText();
    if ("".equals(newName)) {
      newName = null;
    }

    final String newEmail;
    if (emailPick.isEnabled() && emailPick.getSelectedIndex() >= 0) {
      final String v = emailPick.getValue(emailPick.getSelectedIndex());
      if (Util.C.buttonOpenRegisterNewEmail().equals(v)) {
        newEmail = currentEmail;
      } else {
        newEmail = v;
      }
    } else {
      newEmail = currentEmail;
    }

    final ContactInformation info = toContactInformation();
    save.setEnabled(false);
    registerNewEmail.setEnabled(false);

    Util.ACCOUNT_SEC.updateContact(newName, newEmail, info,
        new GerritCallback<Account>() {
          public void onSuccess(final Account result) {
            registerNewEmail.setEnabled(true);
            onSaveSuccess(result);
          }

          @Override
          public void onFailure(final Throwable caught) {
            save.setEnabled(true);
            registerNewEmail.setEnabled(true);
            super.onFailure(caught);
          }
        });
  }

  void onSaveSuccess(final Account result) {
    final Account me = Gerrit.getUserAccount();
    me.setFullName(result.getFullName());
    me.setPreferredEmail(result.getPreferredEmail());
    Gerrit.refreshMenuBar();
    if (accountSettings != null) {
      accountSettings.display(me);
    }
  }

  ContactInformation toContactInformation() {
    return null;
  }
}
