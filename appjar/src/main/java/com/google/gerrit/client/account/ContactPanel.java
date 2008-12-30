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
import com.google.gerrit.client.ui.TextSaveButtonListener;
import com.google.gwt.i18n.client.LocaleInfo;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.ChangeListener;
import com.google.gwt.user.client.ui.ClickListener;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;
import com.google.gwtjsonrpc.client.VoidResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class ContactPanel extends Composite {
  private final AccountSettings parentScreen;
  private int labelIdx, fieldIdx;
  private Grid info;

  private String currentEmail;
  private boolean haveAccount;
  private boolean haveEmails;

  private TextBox nameTxt;
  private ListBox emailPick;
  private TextArea addressTxt;
  private TextBox countryTxt;
  private TextBox phoneTxt;
  private TextBox faxTxt;
  private Button save;

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

    row(0, Util.C.contactFieldFullName(), nameTxt);
    row(1, Util.C.contactFieldEmail(), emailPick);
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
        save.setEnabled(true);
      }
    });
    addressTxt.addKeyboardListener(sbl);
    countryTxt.addKeyboardListener(sbl);
    phoneTxt.addKeyboardListener(sbl);
    faxTxt.addKeyboardListener(sbl);

    initWidget(body);
  }

  @Override
  public void onLoad() {
    super.onLoad();
    display(Gerrit.getUserAccount());

    emailPick.clear();
    emailPick.setEnabled(false);

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
            final List<String> addrs = new ArrayList<String>();
            for (final AccountExternalId i : result) {
              if (i.getEmailAddress() != null
                  && i.getEmailAddress().length() > 0) {
                addrs.add(i.getEmailAddress());
              }
            }
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
      emailPick.setEnabled(true);
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

  private void doSave() {
    final String newName = nameTxt.getText();
    final String newEmail;
    if (emailPick.isEnabled() && emailPick.getSelectedIndex() >= 0) {
      newEmail = emailPick.getValue(emailPick.getSelectedIndex());
    } else {
      newEmail = currentEmail;
    }

    final ContactInformation info = new ContactInformation();
    info.setAddress(addressTxt.getText());
    info.setCountry(countryTxt.getText());
    info.setPhoneNumber(phoneTxt.getText());
    info.setFaxNumber(faxTxt.getText());

    Util.ACCOUNT_SEC.updateContact(newName, newEmail, info,
        new GerritCallback<VoidResult>() {
          public void onSuccess(final VoidResult result) {
            save.setEnabled(false);
            final Account me = Gerrit.getUserAccount();
            me.setFullName(newName);
            me.setPreferredEmail(newEmail);
            me.setContactInformation(info);
            Gerrit.refreshMenuBar();
            parentScreen.display(me);
          }
        });
  }
}
