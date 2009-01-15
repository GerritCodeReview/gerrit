// Copyright 2008 Google Inc.
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
import com.google.gerrit.client.ui.AutoCenterDialogBox;
import com.google.gerrit.client.ui.TextSaveButtonListener;
import com.google.gwt.i18n.client.LocaleInfo;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.ChangeListener;
import com.google.gwt.user.client.ui.ClickListener;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.FormHandler;
import com.google.gwt.user.client.ui.FormPanel;
import com.google.gwt.user.client.ui.FormSubmitCompleteEvent;
import com.google.gwt.user.client.ui.FormSubmitEvent;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;
import com.google.gwtjsonrpc.client.VoidResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class ContactPanel extends Composite {
  private final AccountSettings parentScreen;
  private int labelIdx, fieldIdx;
  private Grid info;

  private String currentEmail;
  private boolean haveAccount;
  private boolean haveEmails;

  private TextBox nameTxt;
  private ListBox emailPick;
  private Button registerNewEmail;
  private TextArea addressTxt;
  private TextBox countryTxt;
  private TextBox phoneTxt;
  private TextBox faxTxt;
  private Button save;

  ContactPanel() {
    this(null);
  }

  ContactPanel(final AccountSettings parent) {
    parentScreen = parent;

    if (LocaleInfo.getCurrentLocale().isRTL()) {
      labelIdx = 1;
      fieldIdx = 0;
    } else {
      labelIdx = 0;
      fieldIdx = 1;
    }

    nameTxt = new TextBox();
    nameTxt.setVisibleLength(60);

    emailPick = new ListBox();

    addressTxt = new TextArea();
    addressTxt.setVisibleLines(4);
    addressTxt.setCharacterWidth(60);

    countryTxt = new TextBox();
    countryTxt.setVisibleLength(40);
    countryTxt.setMaxLength(40);

    phoneTxt = new TextBox();
    phoneTxt.setVisibleLength(30);
    phoneTxt.setMaxLength(30);

    faxTxt = new TextBox();
    faxTxt.setVisibleLength(30);
    faxTxt.setMaxLength(30);

    final FlowPanel body = new FlowPanel();
    info = new Grid(6, 2);
    info.setStyleName("gerrit-InfoBlock");
    info.addStyleName("gerrit-AccountInfoBlock");
    body.add(info);

    registerNewEmail = new Button(Util.C.buttonOpenRegisterNewEmail());
    registerNewEmail.setEnabled(false);
    registerNewEmail.addClickListener(new ClickListener() {
      public void onClick(final Widget sender) {
        doRegisterNewEmail();
      }
    });
    final FlowPanel emailLine = new FlowPanel();
    emailLine.add(emailPick);
    emailLine.add(registerNewEmail);

    row(0, Util.C.contactFieldFullName(), nameTxt);
    row(1, Util.C.contactFieldEmail(), emailLine);
    row(2, Util.C.contactFieldAddress(), addressTxt);
    row(3, Util.C.contactFieldCountry(), countryTxt);
    row(4, Util.C.contactFieldPhone(), phoneTxt);
    row(5, Util.C.contactFieldFax(), faxTxt);

    final CellFormatter fmt = info.getCellFormatter();
    fmt.addStyleName(0, 0, "topmost");
    fmt.addStyleName(0, 1, "topmost");
    fmt.addStyleName(5, 0, "bottomheader");

    save = new Button(Util.C.buttonSaveContact());
    save.setEnabled(false);
    save.addClickListener(new ClickListener() {
      public void onClick(final Widget sender) {
        doSave();
      }
    });
    body.add(save);

    final TextSaveButtonListener sbl = new TextSaveButtonListener(save);
    nameTxt.addKeyboardListener(sbl);
    emailPick.addChangeListener(new ChangeListener() {
      public void onChange(Widget sender) {
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
        }
      }
    });
    addressTxt.addKeyboardListener(sbl);
    countryTxt.addKeyboardListener(sbl);
    phoneTxt.addKeyboardListener(sbl);
    faxTxt.addKeyboardListener(sbl);

    initWidget(body);
  }

  void hideSaveButton() {
    save.setVisible(false);
  }

  @Override
  public void onLoad() {
    super.onLoad();
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
        emailPick.addItem("... " + Util.C.buttonOpenRegisterNewEmail()
            + "  ", Util.C.buttonOpenRegisterNewEmail());
      } else {
        emailPick.setVisible(false);
      }
      registerNewEmail.setEnabled(true);
    }
  }

  private void row(final int row, final String name, final Widget field) {
    info.setText(row, labelIdx, name);
    info.setWidget(row, fieldIdx, field);
    info.getCellFormatter().addStyleName(row, 0, "header");
  }

  private void display(final Account userAccount) {
    ContactInformation info = userAccount.getContactInformation();
    if (info == null) {
      info = new ContactInformation();
    }

    currentEmail = userAccount.getPreferredEmail();
    nameTxt.setText(userAccount.getFullName());
    addressTxt.setText(info.getAddress());
    countryTxt.setText(info.getCountry());
    phoneTxt.setText(info.getPhoneNumber());
    faxTxt.setText(info.getFaxNumber());
    save.setEnabled(false);
  }

  private void doRegisterNewEmail() {
    final AutoCenterDialogBox box = new AutoCenterDialogBox(true, true);
    final VerticalPanel body = new VerticalPanel();

    final TextBox inEmail = new TextBox();
    inEmail.setVisibleLength(60);

    final Button register = new Button(Util.C.buttonSendRegisterNewEmail());
    final FormPanel form = new FormPanel();
    form.addFormHandler(new FormHandler() {
      public void onSubmit(final FormSubmitEvent event) {
        event.setCancelled(true);
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

      public void onSubmitComplete(final FormSubmitCompleteEvent event) {
      }
    });
    form.setWidget(body);

    register.addClickListener(new ClickListener() {
      public void onClick(Widget sender) {
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
    final String newName = nameTxt.getText();
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

    final ContactInformation info = new ContactInformation();
    info.setAddress(addressTxt.getText());
    info.setCountry(countryTxt.getText());
    info.setPhoneNumber(phoneTxt.getText());
    info.setFaxNumber(faxTxt.getText());
    save.setEnabled(false);
    registerNewEmail.setEnabled(false);

    Util.ACCOUNT_SEC.updateContact(newName, newEmail, info,
        new GerritCallback<VoidResult>() {
          public void onSuccess(final VoidResult result) {
            registerNewEmail.setEnabled(false);
            final Account me = Gerrit.getUserAccount();
            me.setFullName(newName);
            me.setPreferredEmail(newEmail);
            me.setContactInformation(info);
            Gerrit.refreshMenuBar();
            if (parentScreen != null) {
              parentScreen.display(me);
            }
          }

          @Override
          public void onFailure(final Throwable caught) {
            save.setEnabled(true);
            registerNewEmail.setEnabled(true);
            super.onFailure(caught);
          }
        });
  }
}
