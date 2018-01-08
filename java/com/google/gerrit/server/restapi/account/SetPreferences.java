// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.restapi.account;

import static com.google.gerrit.server.git.UserConfigSections.CHANGE_TABLE_COLUMN;
import static com.google.gerrit.server.git.UserConfigSections.KEY_ID;
import static com.google.gerrit.server.git.UserConfigSections.KEY_MATCH;
import static com.google.gerrit.server.git.UserConfigSections.KEY_TARGET;
import static com.google.gerrit.server.git.UserConfigSections.KEY_TOKEN;
import static com.google.gerrit.server.git.UserConfigSections.KEY_URL;
import static com.google.gerrit.server.git.UserConfigSections.URL_ALIAS;

import com.google.common.base.Strings;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo;
import com.google.gerrit.extensions.client.MenuItem;
import com.google.gerrit.extensions.config.DownloadScheme;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.account.AccountsUpdate;
import com.google.gerrit.server.account.PreferencesConfig;
import com.google.gerrit.server.account.VersionedAccountPreferences;
import com.google.gerrit.server.git.UserConfigSections;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;

@Singleton
public class SetPreferences implements RestModifyView<AccountResource, GeneralPreferencesInfo> {
  private final Provider<CurrentUser> self;
  private final AccountCache cache;
  private final PermissionBackend permissionBackend;
  private final AccountsUpdate.User accountsUpdate;
  private final DynamicMap<DownloadScheme> downloadSchemes;

  @Inject
  SetPreferences(
      Provider<CurrentUser> self,
      AccountCache cache,
      PermissionBackend permissionBackend,
      AccountsUpdate.User accountsUpdate,
      DynamicMap<DownloadScheme> downloadSchemes) {
    this.self = self;
    this.cache = cache;
    this.permissionBackend = permissionBackend;
    this.accountsUpdate = accountsUpdate;
    this.downloadSchemes = downloadSchemes;
  }

  @Override
  public GeneralPreferencesInfo apply(AccountResource rsrc, GeneralPreferencesInfo input)
      throws AuthException, BadRequestException, IOException, ConfigInvalidException,
          PermissionBackendException, OrmException {
    if (self.get() != rsrc.getUser()) {
      permissionBackend.user(self).check(GlobalPermission.MODIFY_ACCOUNT);
    }

    checkDownloadScheme(input.downloadScheme);
    PreferencesConfig.validateMy(input.my);
    Account.Id id = rsrc.getUser().getAccountId();

    accountsUpdate
        .create()
        .update("Set Preferences via API", id, u -> u.setGeneralPreferences(input));
    return cache.get(id).getGeneralPreferences();
  }

  public static void storeMyMenus(VersionedAccountPreferences prefs, List<MenuItem> my)
      throws BadRequestException {
    Config cfg = prefs.getConfig();
    if (my != null) {
      unsetSection(cfg, UserConfigSections.MY);
      for (MenuItem item : my) {
        checkRequiredMenuItemField(item.name, "name");
        checkRequiredMenuItemField(item.url, "URL");

        set(cfg, item.name, KEY_URL, item.url);
        set(cfg, item.name, KEY_TARGET, item.target);
        set(cfg, item.name, KEY_ID, item.id);
      }
    }
  }

  public static void storeMyChangeTableColumns(
      VersionedAccountPreferences prefs, List<String> changeTable) {
    Config cfg = prefs.getConfig();
    if (changeTable != null) {
      unsetSection(cfg, UserConfigSections.CHANGE_TABLE);
      cfg.setStringList(UserConfigSections.CHANGE_TABLE, null, CHANGE_TABLE_COLUMN, changeTable);
    }
  }

  private static void set(Config cfg, String section, String key, @Nullable String val) {
    if (val == null || val.trim().isEmpty()) {
      cfg.unset(UserConfigSections.MY, section.trim(), key);
    } else {
      cfg.setString(UserConfigSections.MY, section.trim(), key, val.trim());
    }
  }

  private static void unsetSection(Config cfg, String section) {
    cfg.unsetSection(section, null);
    for (String subsection : cfg.getSubsections(section)) {
      cfg.unsetSection(section, subsection);
    }
  }

  public static void storeUrlAliases(
      VersionedAccountPreferences prefs, Map<String, String> urlAliases) {
    if (urlAliases != null) {
      Config cfg = prefs.getConfig();
      for (String subsection : cfg.getSubsections(URL_ALIAS)) {
        cfg.unsetSection(URL_ALIAS, subsection);
      }

      int i = 1;
      for (Entry<String, String> e : urlAliases.entrySet()) {
        cfg.setString(URL_ALIAS, URL_ALIAS + i, KEY_MATCH, e.getKey());
        cfg.setString(URL_ALIAS, URL_ALIAS + i, KEY_TOKEN, e.getValue());
        i++;
      }
    }
  }

  private static void checkRequiredMenuItemField(String value, String name)
      throws BadRequestException {
    if (value == null || value.trim().isEmpty()) {
      throw new BadRequestException(name + " for menu item is required");
    }
  }

  private void checkDownloadScheme(String downloadScheme) throws BadRequestException {
    if (Strings.isNullOrEmpty(downloadScheme)) {
      return;
    }

    for (DynamicMap.Entry<DownloadScheme> e : downloadSchemes) {
      if (e.getExportName().equals(downloadScheme) && e.getProvider().get().isEnabled()) {
        return;
      }
    }
    throw new BadRequestException("Unsupported download scheme: " + downloadScheme);
  }
}
