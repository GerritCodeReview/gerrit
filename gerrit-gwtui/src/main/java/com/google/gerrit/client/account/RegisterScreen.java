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

package com.google.gerrit.client.account;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.info.AccountInfo;
import com.google.gerrit.client.ui.AccountScreen;
import com.google.gerrit.client.ui.InlineHyperlink;
import com.google.gerrit.client.ui.SmallHeading;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.extensions.client.AccountFieldName;
import com.google.gwt.i18n.client.LocaleInfo;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.FormPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;

public class RegisterScreen extends AccountScreen {
  private final String nextToken;

  public RegisterScreen(String next) {
    nextToken = next;
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    display();
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();
    setPageTitle(Util.C.welcomeToGerritCodeReview());

    final FlowPanel formBody = new FlowPanel();

    final FlowPanel contactGroup = new FlowPanel();
    contactGroup.setStyleName(Gerrit.RESOURCES.css().registerScreenSection());
    contactGroup.add(new SmallHeading(Util.C.welcomeReviewContact()));
    final HTML whereFrom = new HTML(Util.C.welcomeContactFrom());
    whereFrom.setStyleName(Gerrit.RESOURCES.css().registerScreenExplain());
    contactGroup.add(whereFrom);
    contactGroup.add(
        new ContactPanelShort() {
          @Override
          protected void display(AccountInfo account) {
            super.display(account);

            if ("".equals(nameTxt.getText())) {
              // No name? Encourage the user to provide us something.
              //
              nameTxt.setFocus(true);
              save.setEnabled(true);
            }
          }
        });
    formBody.add(contactGroup);

    if (Gerrit.getUserAccount().username() == null
        && Gerrit.info().auth().canEdit(AccountFieldName.USER_NAME)) {
      final FlowPanel fp = new FlowPanel();
      fp.setStyleName(Gerrit.RESOURCES.css().registerScreenSection());
      fp.add(new SmallHeading(Util.C.welcomeUsernameHeading()));

      final Grid userInfo = new Grid(1, 2);
      final CellFormatter fmt = userInfo.getCellFormatter();
      userInfo.setStyleName(Gerrit.RESOURCES.css().infoBlock());
      userInfo.addStyleName(Gerrit.RESOURCES.css().accountInfoBlock());
      fp.add(userInfo);

      fmt.addStyleName(0, 0, Gerrit.RESOURCES.css().topmost());
      fmt.addStyleName(0, 1, Gerrit.RESOURCES.css().topmost());
      fmt.addStyleName(0, 0, Gerrit.RESOURCES.css().bottomheader());

      UsernameField field = new UsernameField();
      if (LocaleInfo.getCurrentLocale().isRTL()) {
        userInfo.setText(0, 1, Util.C.userName());
        userInfo.setWidget(0, 0, field);
        fmt.addStyleName(0, 1, Gerrit.RESOURCES.css().header());
      } else {
        userInfo.setText(0, 0, Util.C.userName());
        userInfo.setWidget(0, 1, field);
        fmt.addStyleName(0, 0, Gerrit.RESOURCES.css().header());
      }

      formBody.add(fp);
    }

    if (Gerrit.info().hasSshd()) {
      final FlowPanel sshKeyGroup = new FlowPanel();
      sshKeyGroup.setStyleName(Gerrit.RESOURCES.css().registerScreenSection());
      sshKeyGroup.add(new SmallHeading(Util.C.welcomeSshKeyHeading()));
      final HTML whySshKey = new HTML(Util.C.welcomeSshKeyText());
      whySshKey.setStyleName(Gerrit.RESOURCES.css().registerScreenExplain());
      sshKeyGroup.add(whySshKey);
      sshKeyGroup.add(
          new SshPanel() {
            {
              setKeyTableVisible(false);
            }
          });
      formBody.add(sshKeyGroup);
    }

    final FlowPanel choices = new FlowPanel();
    choices.setStyleName(Gerrit.RESOURCES.css().registerScreenNextLinks());
    if (Gerrit.info().auth().useContributorAgreements()) {
      final FlowPanel agreementGroup = new FlowPanel();
      agreementGroup.setStyleName(Gerrit.RESOURCES.css().registerScreenSection());
      agreementGroup.add(new SmallHeading(Util.C.welcomeAgreementHeading()));
      final HTML whyAgreement = new HTML(Util.C.welcomeAgreementText());
      whyAgreement.setStyleName(Gerrit.RESOURCES.css().registerScreenExplain());
      agreementGroup.add(whyAgreement);

      choices.add(new InlineHyperlink(Util.C.newAgreement(), PageLinks.SETTINGS_NEW_AGREEMENT));
      choices.add(new InlineHyperlink(Util.C.welcomeAgreementLater(), nextToken));
      formBody.add(agreementGroup);
    } else {
      choices.add(new InlineHyperlink(Util.C.welcomeContinue(), nextToken));
    }
    formBody.add(choices);

    final FormPanel form = new FormPanel();
    form.add(formBody);
    add(form);
  }
}
