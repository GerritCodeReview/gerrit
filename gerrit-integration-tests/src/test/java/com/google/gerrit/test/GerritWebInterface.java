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

package com.google.gerrit.test;

import com.google.gerrit.test.pageobjects.BecomeAnyAccount;
import com.google.gerrit.test.pageobjects.GroupListScreen;
import com.google.gerrit.test.pageobjects.MyProfileScreen;
import com.google.gerrit.test.pageobjects.ProjectListScreen;
import com.google.gerrit.test.pageobjects.QueryScreen;
import com.google.gerrit.test.pageobjects.RegisterScreen;
import com.google.gerrit.test.pageobjects.UserPassSignInDialog;
import com.google.gerrit.test.util.Check;
import com.google.gerrit.test.util.WaitUtil;

import org.eclipse.jgit.lib.PersonIdent;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.LinkedList;
import java.util.List;

public class GerritWebInterface {

  private static final Logger log = LoggerFactory
      .getLogger(GerritWebInterface.class);

  private final static int HTTP_TIMEOUT = GerritTestProperty.HTTP_TIMEOUT.get();
  private final static int HTTP_POLLING_INTERVAL =
      GerritTestProperty.HTTP_POLLING_INTERVAL.get();

  private final URL gerritUrl;
  private final WebDriver driver;

  public GerritWebInterface(final URL url) {
    gerritUrl = url;
    driver = GerritTestProperty.WEBDRIVER.get().createDriver();
    log.info("opening web driver: " + driver.getClass().getName());
  }

  void createInitialAdminUser(final String username,
      final String fullName, final String email, final String publicSshKey) {
    BecomeAnyAccount becomeAnyAccount = BecomeAnyAccount.open(driver, gerritUrl);
    RegisterScreen registerScreen = becomeAnyAccount.clickNewAccount();
    registerScreen.registerUser(username, fullName, email, publicSshKey);
  }

  static void waitUntilReachable(final URL url) {
    log.info("Testing HTTP Connection...");

    final Check checkThatReachable = new Check() {
      @Override
      public boolean hasFinished() {
        return isReachable(url);
      }
    };
    WaitUtil.wait(checkThatReachable, HTTP_TIMEOUT, HTTP_POLLING_INTERVAL, log,
        "Timeout during HTTP communication with server. HTTP ping failed.");

    log.info("HTTP Connection successfully tested.");
  }

  private static boolean isReachable(final URL url) {
    try {
      final URLConnection connection = url.openConnection();
      connection.connect();
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  public void login(final String username, final String fullname, final String password) {
    if (password == null) {
      loginDevelopmentBecomeAnyAccount(username, fullname);
    } else {
      loginLdap(username, password);
    }
  }

  private void loginDevelopmentBecomeAnyAccount(final String username,
      final String fullname) {
    final BecomeAnyAccount becomeAnyAccount =
        BecomeAnyAccount.open(driver, gerritUrl);
    becomeAnyAccount.signInWithUserName(username, fullname);
  }

  private void loginLdap(final String username, final String password) {
    final QueryScreen queryScreen =
        QueryScreen.open(driver, gerritUrl, "status:open");
    final UserPassSignInDialog signInDialog =
        UserPassSignInDialog.open(queryScreen);
    signInDialog.signIn(username, password);
  }

  public PersonIdent getPersonIdent() {
    final MyProfileScreen profileScreen =
        MyProfileScreen.open(driver, gerritUrl);
    return profileScreen.getPersonIdent();
  }

  public void logout() {
    driver.get(gerritUrl + "/logout");
    // after logout we should get redirected to the QueryScreen, wait for this
    // screen to be loaded to ensure that logout was finished
    final QueryScreen queryScreen = new QueryScreen(driver, "status:open");
    queryScreen.waitForPage();
  }

  public List<String> listGroups() {
    GroupListScreen adminGroups = GroupListScreen.open(driver, gerritUrl);
    return adminGroups.getGroups();
  }

  public List<String> listProjects() {
    final ProjectListScreen adminProjects =
        ProjectListScreen.open(driver, gerritUrl);
    return adminProjects.getProjects();
  }

  public List<Change> listAllOpenChanges() {
    final List<Change> changes = new LinkedList<Change>();
    QueryScreen queryScreen = QueryScreen.open(driver, gerritUrl, "status:open");
    changes.addAll(queryScreen.getChanges());
    while (queryScreen.hasNextPage()) {
      queryScreen = queryScreen.nextPage();
      changes.addAll(queryScreen.getChanges());
    }
    return changes;
  }

  public void close() {
    log.info("closing web driver");
    driver.quit();
  }
}
