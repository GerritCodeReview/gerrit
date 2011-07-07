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

import org.eclipse.jgit.lib.PersonIdent;
import org.openqa.selenium.WebDriver;

import java.net.URL;

public class MyProfileScreen extends PageObject {

  public static MyProfileScreen open(final WebDriver driver,
      final URL gerritUrl) {
    final String url = gerritUrl.toString() + "/#/settings/";
    return PageObject.get(url, MyProfileScreen.class, driver);
  }

  private MyProfileScreen(final WebDriver driver) {
    super(driver);
  }

  @Override
  protected String getExpectedTitle() {
    return "Settings";
  }

  public PersonIdent getPersonIdent() {
    final String name = getTableField(ACCOUNT_CONSTANTS.fullName(), 1);
    final String email = getTableField(ACCOUNT_CONSTANTS.preferredEmail(), 1);
    return new PersonIdent(name, email);
  }
}
