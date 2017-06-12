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
import com.google.gerrit.client.GerritUiExtensionPoint;
import com.google.gerrit.client.api.ExtensionPanel;
import com.google.gerrit.client.api.ExtensionSettingsScreen;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.client.ui.MenuScreen;
import com.google.gerrit.common.PageLinks;
import java.util.HashSet;
import java.util.Set;

public abstract class SettingsScreen extends MenuScreen {
  private final Set<String> allMenuNames;
  private final Set<String> ambiguousMenuNames;

  public SettingsScreen() {
    setRequiresSignIn(true);

    allMenuNames = new HashSet<>();
    ambiguousMenuNames = new HashSet<>();

    linkByGerrit(Util.C.tabAccountSummary(), PageLinks.SETTINGS);
    linkByGerrit(Util.C.tabPreferences(), PageLinks.SETTINGS_PREFERENCES);
    linkByGerrit(Util.C.tabDiffPreferences(), PageLinks.SETTINGS_DIFF_PREFERENCES);
    linkByGerrit(Util.C.tabEditPreferences(), PageLinks.SETTINGS_EDIT_PREFERENCES);
    linkByGerrit(Util.C.tabWatchedProjects(), PageLinks.SETTINGS_PROJECTS);
    linkByGerrit(Util.C.tabContactInformation(), PageLinks.SETTINGS_CONTACT);
    if (Gerrit.info().hasSshd()) {
      linkByGerrit(Util.C.tabSshKeys(), PageLinks.SETTINGS_SSHKEYS);
    }
    if (Gerrit.info().auth().isHttpPasswordSettingsEnabled()) {
      linkByGerrit(Util.C.tabHttpAccess(), PageLinks.SETTINGS_HTTP_PASSWORD);
    }
    if (Gerrit.info().auth().isOAuth() && Gerrit.info().auth().isGitBasicAuth()) {
      linkByGerrit(Util.C.tabOAuthToken(), PageLinks.SETTINGS_OAUTH_TOKEN);
    }
    if (Gerrit.info().gerrit().editGpgKeys()) {
      linkByGerrit(Util.C.tabGpgKeys(), PageLinks.SETTINGS_GPGKEYS);
    }
    linkByGerrit(Util.C.tabWebIdentities(), PageLinks.SETTINGS_WEBIDENT);
    linkByGerrit(Util.C.tabMyGroups(), PageLinks.SETTINGS_MYGROUPS);
    if (Gerrit.info().auth().useContributorAgreements()) {
      linkByGerrit(Util.C.tabAgreements(), PageLinks.SETTINGS_AGREEMENTS);
    }

    for (String pluginName : ExtensionSettingsScreen.Definition.plugins()) {
      for (ExtensionSettingsScreen.Definition def :
          Natives.asList(ExtensionSettingsScreen.Definition.get(pluginName))) {
        if (!allMenuNames.add(def.getMenu())) {
          ambiguousMenuNames.add(def.getMenu());
        }
      }
    }

    for (String pluginName : ExtensionSettingsScreen.Definition.plugins()) {
      for (ExtensionSettingsScreen.Definition def :
          Natives.asList(ExtensionSettingsScreen.Definition.get(pluginName))) {
        linkByPlugin(pluginName, def.getMenu(), PageLinks.toSettings(pluginName, def.getPath()));
      }
    }
  }

  private void linkByGerrit(String text, String target) {
    allMenuNames.add(text);
    link(text, target);
  }

  private void linkByPlugin(String pluginName, String text, String target) {
    if (ambiguousMenuNames.contains(text)) {
      text += " (" + pluginName + ")";
    }
    link(text, target);
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();
    setPageTitle(Util.C.settingsHeading());
  }

  protected ExtensionPanel createExtensionPoint(GerritUiExtensionPoint extensionPoint) {
    ExtensionPanel extensionPanel = new ExtensionPanel(extensionPoint);
    extensionPanel.putObject(GerritUiExtensionPoint.Key.ACCOUNT_INFO, Gerrit.getUserAccount());
    return extensionPanel;
  }
}
