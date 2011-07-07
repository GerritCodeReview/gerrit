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

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.net.URL;

public class BecomeAnyAccount extends PageObject {

  public static BecomeAnyAccount open(WebDriver driver, URL gerritUrl) {
    final String url = gerritUrl.toString() + "/become";
    BecomeAnyAccount page = PageObject.get(url, BecomeAnyAccount.class, driver);
    return page;
  }

  private BecomeAnyAccount(WebDriver driver) {
    super(driver);
  }

  @Override
  protected String getExpectedTitle() {
    return "Gerrit Code Review";
  }

  public RegisterScreen clickNewAccount() {
    final WebElement newAccountButton =
      waitUntilFound("input", "value", "New Account");
    return navigateByClick(newAccountButton, RegisterScreen.class);
  }

  public AccountDashboardScreen signInWithUserName(String username,
      String fullname) {
    final WebElement userName = waitUntilFound("input", "name", "user_name");
    userName.sendKeys(username);
    final WebElement becomeButton = find("input", "value", "Become Account");
    return navigateByClick(becomeButton, AccountDashboardScreen.class, fullname);
  }
}
