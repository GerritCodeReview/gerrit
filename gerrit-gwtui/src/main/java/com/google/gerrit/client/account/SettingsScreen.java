// Copyright (C) 2010 The Android Open Source Project
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
import com.google.gerrit.client.ui.MenuScreen;
import com.google.gerrit.common.PageLinks;

public abstract class SettingsScreen extends MenuScreen {
  public SettingsScreen() {
    setRequiresSignIn(true);

    link(Util.C.tabAccountSummary(), PageLinks.SETTINGS);
    link(Util.C.tabPreferences(), PageLinks.SETTINGS_PREFERENCES);
    link(Util.C.tabWatchedProjects(), PageLinks.SETTINGS_PROJECTS);
    link(Util.C.tabContactInformation(), PageLinks.SETTINGS_CONTACT);
    if (Gerrit.getConfig().getSshdAddress() != null) {
      link(Util.C.tabSshKeys(), PageLinks.SETTINGS_SSHKEYS);
    }
    link(Util.C.tabHttpAccess(), PageLinks.SETTINGS_HTTP_PASSWORD);
    link(Util.C.tabWebIdentities(), PageLinks.SETTINGS_WEBIDENT);
    link(Util.C.tabMyGroups(), PageLinks.SETTINGS_MYGROUPS);
    if (Gerrit.getConfig().isUseContributorAgreements()) {
      link(Util.C.tabAgreements(), PageLinks.SETTINGS_AGREEMENTS);
    }
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();
    setPageTitle(Util.C.settingsHeading());
  }
}
