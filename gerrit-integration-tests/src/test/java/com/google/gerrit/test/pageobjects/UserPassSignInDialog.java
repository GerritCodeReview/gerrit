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

import static com.google.gerrit.test.GerritConstants.GERRIT_CONSTANTS;
import static com.google.gerrit.test.GerritConstants.USER_PASS_CONSTANTS;

public class UserPassSignInDialog {

  private final PageObject page;

  public static UserPassSignInDialog open(final PageObject page) {
    page.clickLink(GERRIT_CONSTANTS.menuSignIn());
    page.waitUntilFound("div", GERRIT_CONSTANTS.signInDialogTitle());
    return new UserPassSignInDialog(page);
  }

  private UserPassSignInDialog(final PageObject page) {
    this.page = page;
  }

  public void signIn(final String user, final String password) {
    page.setTableTextField(USER_PASS_CONSTANTS.username(), user);
    page.setTableTextField(USER_PASS_CONSTANTS.password(), password, false);
    page.clickButton(USER_PASS_CONSTANTS.buttonSignIn());

    page.waitUntilFound("a", GERRIT_CONSTANTS.menuSignOut());
  }
}
