// Copyright (C) 2015 The Android Open Source Project
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

import static com.google.gerrit.server.config.ConfigUtil.loadSection;
import static com.google.gerrit.server.config.ConfigUtil.skipField;
import static com.google.gerrit.server.git.UserConfigSections.KEY_ID;
import static com.google.gerrit.server.git.UserConfigSections.KEY_MATCH;
import static com.google.gerrit.server.git.UserConfigSections.KEY_TARGET;
import static com.google.gerrit.server.git.UserConfigSections.KEY_TOKEN;
import static com.google.gerrit.server.git.UserConfigSections.KEY_URL;
import static com.google.gerrit.server.git.UserConfigSections.URL_ALIAS;

import com.google.common.base.Strings;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo;
import com.google.gerrit.extensions.client.MenuItem;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.UserConfigSections;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class GeneralPreferencesLoader {
  private static final Logger log = LoggerFactory.getLogger(GeneralPreferencesLoader.class);

  private final GitRepositoryManager gitMgr;
  private final AllUsersName allUsersName;

  @Inject
  public GeneralPreferencesLoader(GitRepositoryManager gitMgr, AllUsersName allUsersName) {
    this.gitMgr = gitMgr;
    this.allUsersName = allUsersName;
  }

  public GeneralPreferencesInfo load(Account.Id id)
      throws IOException, ConfigInvalidException, RepositoryNotFoundException {
    return read(id, null);
  }

  public GeneralPreferencesInfo merge(Account.Id id, GeneralPreferencesInfo in)
      throws IOException, ConfigInvalidException, RepositoryNotFoundException {
    return read(id, in);
  }

  private GeneralPreferencesInfo read(Account.Id id, GeneralPreferencesInfo in)
      throws IOException, ConfigInvalidException, RepositoryNotFoundException {
    try (Repository allUsers = gitMgr.openRepository(allUsersName)) {
      // Load all users default prefs
      VersionedAccountPreferences dp = VersionedAccountPreferences.forDefault();
      dp.load(allUsers);
      GeneralPreferencesInfo allUserPrefs = new GeneralPreferencesInfo();
      loadSection(
          dp.getConfig(),
          UserConfigSections.GENERAL,
          null,
          allUserPrefs,
          GeneralPreferencesInfo.defaults(),
          in);

      // Load user prefs
      VersionedAccountPreferences p = VersionedAccountPreferences.forUser(id);
      p.load(allUsers);
      GeneralPreferencesInfo r =
          loadSection(
              p.getConfig(),
              UserConfigSections.GENERAL,
              null,
              new GeneralPreferencesInfo(),
              updateDefaults(allUserPrefs),
              in);

      return loadMyMenusAndUrlAliases(r, p, dp);
    }
  }

  private GeneralPreferencesInfo updateDefaults(GeneralPreferencesInfo input) {
    GeneralPreferencesInfo result = GeneralPreferencesInfo.defaults();
    try {
      for (Field field : input.getClass().getDeclaredFields()) {
        if (skipField(field)) {
          continue;
        }
        Object newVal = field.get(input);
        if (newVal != null) {
          field.set(result, newVal);
        }
      }
    } catch (IllegalAccessException e) {
      log.error("Cannot get default general preferences from " + allUsersName.get(), e);
      return GeneralPreferencesInfo.defaults();
    }
    return result;
  }

  public GeneralPreferencesInfo loadMyMenusAndUrlAliases(
      GeneralPreferencesInfo r, VersionedAccountPreferences v, VersionedAccountPreferences d) {
    r.my = my(v);
    if (r.my.isEmpty() && !v.isDefaults()) {
      r.my = my(d);
    }
    if (r.my.isEmpty()) {
      r.my.add(new MenuItem("Changes", "#/dashboard/self", null));
      r.my.add(new MenuItem("Drafts", "#/q/owner:self+is:draft", null));
      r.my.add(new MenuItem("Draft Comments", "#/q/has:draft", null));
      r.my.add(new MenuItem("Edits", "#/q/has:edit", null));
      r.my.add(new MenuItem("Watched Changes", "#/q/is:watched+is:open", null));
      r.my.add(new MenuItem("Starred Changes", "#/q/is:starred", null));
      r.my.add(new MenuItem("Groups", "#/groups/self", null));
    }

    r.urlAliases = urlAliases(v);
    if (r.urlAliases == null && !v.isDefaults()) {
      r.urlAliases = urlAliases(d);
    }
    return r;
  }

  private static List<MenuItem> my(VersionedAccountPreferences v) {
    List<MenuItem> my = new ArrayList<>();
    Config cfg = v.getConfig();
    for (String subsection : cfg.getSubsections(UserConfigSections.MY)) {
      String url = my(cfg, subsection, KEY_URL, "#/");
      String target = my(cfg, subsection, KEY_TARGET, url.startsWith("#") ? null : "_blank");
      my.add(new MenuItem(subsection, url, target, my(cfg, subsection, KEY_ID, null)));
    }
    return my;
  }

  private static String my(Config cfg, String subsection, String key, String defaultValue) {
    String val = cfg.getString(UserConfigSections.MY, subsection, key);
    return !Strings.isNullOrEmpty(val) ? val : defaultValue;
  }

  private static Map<String, String> urlAliases(VersionedAccountPreferences v) {
    HashMap<String, String> urlAliases = new HashMap<>();
    Config cfg = v.getConfig();
    for (String subsection : cfg.getSubsections(URL_ALIAS)) {
      urlAliases.put(
          cfg.getString(URL_ALIAS, subsection, KEY_MATCH),
          cfg.getString(URL_ALIAS, subsection, KEY_TOKEN));
    }
    return !urlAliases.isEmpty() ? urlAliases : null;
  }
}
