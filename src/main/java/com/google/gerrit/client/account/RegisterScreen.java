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
import com.google.gerrit.client.Link;
import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.ui.AccountScreen;
import com.google.gerrit.client.ui.SmallHeading;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.FormPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.InlineHyperlink;

public class RegisterScreen extends AccountScreen {
  private final String nextToken;

  public RegisterScreen(final String next) {
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
    contactGroup.setStyleName("gerrit-RegisterScreen-Section");
    contactGroup.add(new SmallHeading(Util.C.welcomeReviewContact()));
    final HTML whereFrom = new HTML(Util.C.welcomeContactFrom());
    whereFrom.setStyleName("gerrit-RegisterScreen-Explain");
    contactGroup.add(whereFrom);
    contactGroup.add(new ContactPanelShort() {
      @Override
      protected void display(final Account userAccount) {
        super.display(userAccount);

        if ("".equals(nameTxt.getText())) {
          // No name? Encourage the user to provide us something.
          //
          nameTxt.setFocus(true);
          save.setEnabled(true);
        }
      }
    });
    formBody.add(contactGroup);

    final FlowPanel sshKeyGroup = new FlowPanel();
    sshKeyGroup.setStyleName("gerrit-RegisterScreen-Section");
    sshKeyGroup.add(new SmallHeading(Util.C.welcomeSshKeyHeading()));
    final HTML whySshKey = new HTML(Util.C.welcomeSshKeyText());
    whySshKey.setStyleName("gerrit-RegisterScreen-Explain");
    sshKeyGroup.add(whySshKey);
    sshKeyGroup.add(new SshKeyPanel() {
      {
        setKeyTableVisible(false);
      }
    });
    formBody.add(sshKeyGroup);

    final FlowPanel choices = new FlowPanel();
    choices.setStyleName("gerrit-RegisterScreen-NextLinks");
    if (Gerrit.getConfig().isUseContributorAgreements()) {
      final FlowPanel agreementGroup = new FlowPanel();
      agreementGroup.setStyleName("gerrit-RegisterScreen-Section");
      agreementGroup.add(new SmallHeading(Util.C.welcomeAgreementHeading()));
      final HTML whyAgreement = new HTML(Util.C.welcomeAgreementText());
      whyAgreement.setStyleName("gerrit-RegisterScreen-Explain");
      agreementGroup.add(whyAgreement);

      choices.add(new InlineHyperlink(Util.C.newAgreement(),
          Link.SETTINGS_NEW_AGREEMENT + "," + nextToken));
      choices
          .add(new InlineHyperlink(Util.C.welcomeAgreementLater(), nextToken));
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
