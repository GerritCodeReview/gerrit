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

package com.google.gerrit.server.account;

import static com.google.gerrit.server.config.ConfigUtil.storeSection;
import static com.google.gerrit.server.git.UserConfigSections.KEY_ID;
import static com.google.gerrit.server.git.UserConfigSections.KEY_MATCH;
import static com.google.gerrit.server.git.UserConfigSections.KEY_TARGET;
import static com.google.gerrit.server.git.UserConfigSections.KEY_TOKEN;
import static com.google.gerrit.server.git.UserConfigSections.KEY_URL;
import static com.google.gerrit.server.git.UserConfigSections.URL_ALIAS;

import com.google.common.base.Strings;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo;
import com.google.gerrit.extensions.client.MenuItem;
import com.google.gerrit.extensions.config.DownloadScheme;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.UserConfigSections;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Config;

@Singleton
public class SetPreferences implements RestModifyView<AccountResource, GeneralPreferencesInfo> {
  private final Provider<CurrentUser> self;
  private final AccountCache cache;
  private final GeneralPreferencesLoader loader;
  private final Provider<MetaDataUpdate.User> metaDataUpdateFactory;
  private final AllUsersName allUsersName;
  private final DynamicMap<DownloadScheme> downloadSchemes;

  @Inject
  SetPreferences(
      Provider<CurrentUser> self,
      AccountCache cache,
      GeneralPreferencesLoader loader,
      Provider<MetaDataUpdate.User> metaDataUpdateFactory,
      AllUsersName allUsersName,
      DynamicMap<DownloadScheme> downloadSchemes) {
    this.self = self;
    this.loader = loader;
    this.cache = cache;
    this.metaDataUpdateFactory = metaDataUpdateFactory;
    this.allUsersName = allUsersName;
    this.downloadSchemes = downloadSchemes;
  }

  @Override
  public GeneralPreferencesInfo apply(AccountResource rsrc, GeneralPreferencesInfo i)
      throws AuthException, BadRequestException, IOException, ConfigInvalidException {
    if (self.get() != rsrc.getUser() && !self.get().getCapabilities().canModifyAccount()) {
      throw new AuthException("requires Modify Account capability");
    }

    checkDownloadScheme(i.downloadScheme);
    Account.Id id = rsrc.getUser().getAccountId();
    GeneralPreferencesInfo n = loader.merge(id, i);

    n.my = i.my;
    n.urlAliases = i.urlAliases;

    writeToGit(id, n);

    return cache.get(id).getAccount().getGeneralPreferencesInfo();
  }

  private void writeToGit(Account.Id id, GeneralPreferencesInfo i)
      throws RepositoryNotFoundException, IOException, ConfigInvalidException {
    VersionedAccountPreferences prefs;
    try (MetaDataUpdate md = metaDataUpdateFactory.get().create(allUsersName)) {
      prefs = VersionedAccountPreferences.forUser(id);
      prefs.load(md);

      storeSection(
          prefs.getConfig(),
          UserConfigSections.GENERAL,
          null,
          i,
          GeneralPreferencesInfo.defaults());

      storeMyMenus(prefs, i.my);
      storeUrlAliases(prefs, i.urlAliases);
      prefs.commit(md);
      cache.evict(id);
    }
  }

  public static void storeMyMenus(VersionedAccountPreferences prefs, List<MenuItem> my) {
    Config cfg = prefs.getConfig();
    if (my != null) {
      unsetSection(cfg, UserConfigSections.MY);
      for (MenuItem item : my) {
        set(cfg, item.name, KEY_URL, item.url);
        set(cfg, item.name, KEY_TARGET, item.target);
        set(cfg, item.name, KEY_ID, item.id);
      }
    }
  }

  private static void set(Config cfg, String section, String key, String val) {
    if (Strings.isNullOrEmpty(val)) {
      cfg.unset(UserConfigSections.MY, section, key);
    } else {
      cfg.setString(UserConfigSections.MY, section, key, val);
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
