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
import com.google.gerrit.client.ui.OnEditEnabler;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.ContactInformation;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Label;
import com.google.gwtexpui.globalkey.client.NpTextArea;
import com.google.gwtexpui.globalkey.client.NpTextBox;

import java.sql.Timestamp;
import java.util.Date;

class ContactPanelFull extends ContactPanelShort {
  private Label hasContact;
  private NpTextArea addressTxt;
  private NpTextBox countryTxt;
  private NpTextBox phoneTxt;
  private NpTextBox faxTxt;

  @Override
  protected void onInitUI() {
    super.onInitUI();

    addressTxt = new NpTextArea();
    addressTxt.setVisibleLines(4);
    addressTxt.setCharacterWidth(60);

    countryTxt = new NpTextBox();
    countryTxt.setVisibleLength(40);
    countryTxt.setMaxLength(40);

    phoneTxt = new NpTextBox();
    phoneTxt.setVisibleLength(30);
    phoneTxt.setMaxLength(30);

    faxTxt = new NpTextBox();
    faxTxt.setVisibleLength(30);
    faxTxt.setMaxLength(30);

    final Grid infoSecure = new Grid(4, 2);
    infoSecure.setStyleName(Gerrit.RESOURCES.css().infoBlock());
    infoSecure.addStyleName(Gerrit.RESOURCES.css().accountInfoBlock());

    final HTML privhtml = new HTML(Util.C.contactPrivacyDetailsHtml());
    privhtml.setStyleName(Gerrit.RESOURCES.css().accountContactPrivacyDetails());

    hasContact = new Label();
    hasContact.setStyleName(Gerrit.RESOURCES.css().accountContactOnFile());
    hasContact.setVisible(false);

    if (Gerrit.getConfig().isUseContactInfo()) {
      body.add(privhtml);
      body.add(hasContact);
      body.add(infoSecure);
    }

    row(infoSecure, 0, Util.C.contactFieldAddress(), addressTxt);
    row(infoSecure, 1, Util.C.contactFieldCountry(), countryTxt);
    row(infoSecure, 2, Util.C.contactFieldPhone(), phoneTxt);
    row(infoSecure, 3, Util.C.contactFieldFax(), faxTxt);

    infoSecure.getCellFormatter().addStyleName(0, 0, Gerrit.RESOURCES.css().topmost());
    infoSecure.getCellFormatter().addStyleName(0, 1, Gerrit.RESOURCES.css().topmost());
    infoSecure.getCellFormatter().addStyleName(3, 0, Gerrit.RESOURCES.css().bottomheader());

    final OnEditEnabler sbl = new OnEditEnabler(save);
    sbl.listenTo(addressTxt);
    sbl.listenTo(countryTxt);
    sbl.listenTo(phoneTxt);
    sbl.listenTo(faxTxt);
  }

  @Override
  protected void display(final Account userAccount) {
    super.display(userAccount);
    displayHasContact(userAccount);
    addressTxt.setText("");
    countryTxt.setText("");
    phoneTxt.setText("");
    faxTxt.setText("");
  }

  private void displayHasContact(final Account userAccount) {
    if (userAccount.isContactFiled()) {
      final Timestamp dt = userAccount.getContactFiledOn();
      hasContact.setText(Util.M.contactOnFile(new Date(dt.getTime())));
      hasContact.setVisible(true);
    } else {
      hasContact.setVisible(false);
    }
  }

  @Override
  void onSaveSuccess(final Account userAccount) {
    super.onSaveSuccess(userAccount);
    displayHasContact(userAccount);
  }

  @Override
  ContactInformation toContactInformation() {
    final ContactInformation info;
    if (Gerrit.getConfig().isUseContactInfo()) {
      info = new ContactInformation();
      info.setAddress(addressTxt.getText());
      info.setCountry(countryTxt.getText());
      info.setPhoneNumber(phoneTxt.getText());
      info.setFaxNumber(faxTxt.getText());
    } else {
      info = null;
    }
    return info;
  }
}
