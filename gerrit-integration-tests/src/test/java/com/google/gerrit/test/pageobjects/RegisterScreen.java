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

package com.google.gerrit.test.pageobjects;

import static com.google.gerrit.test.GerritConstants.ACCOUNT_CONSTANTS;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

public class RegisterScreen extends PageObject {

  private RegisterScreen(WebDriver driver) {
    super(driver);
  }

  @Override
  protected String getExpectedTitle() {
    return ACCOUNT_CONSTANTS.welcomeToGerritCodeReview();
  }

  public AccountDashboardScreen registerUser(final String username,
      final String fullName, final String email, final String publicSshKey) {

    setTableTextField(ACCOUNT_CONSTANTS.fullName(), fullName);
    registerEmail(email);
    clickButton(ACCOUNT_CONSTANTS.buttonSaveChanges());

    setTableTextField(ACCOUNT_CONSTANTS.userName(), username);
    clickButton(ACCOUNT_CONSTANTS.buttonSetUserName());

    setText(
        find("div", ACCOUNT_CONSTANTS.addSshKeyPanelHeader()).findElement(
            By.xpath("..//..//..//textarea")), publicSshKey);
    clickButton(ACCOUNT_CONSTANTS.buttonAddSshKey());

    clickLink(ACCOUNT_CONSTANTS.welcomeContinue());
    return createAsserting(AccountDashboardScreen.class, driver, fullName);
  }

  private void registerEmail(final String email) {
    clickButton(ACCOUNT_CONSTANTS.buttonOpenRegisterNewEmail());

    final WebElement emailTextBox =
        waitUntilFound(By.className("dialogContent")).findElement(
            By.xpath("..//input"));
    setText(emailTextBox, email);
    clickButton(ACCOUNT_CONSTANTS.buttonSendRegisterNewEmail());
  }
}
