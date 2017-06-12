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

import static com.google.gerrit.server.config.ConfigUtil.loadSection;
import static com.google.gerrit.server.config.ConfigUtil.skipField;

import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.UserConfigSections;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.lang.reflect.Field;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class GetDiffPreferences implements RestReadView<AccountResource> {
  private static final Logger log = LoggerFactory.getLogger(GetDiffPreferences.class);

  private final Provider<CurrentUser> self;
  private final Provider<AllUsersName> allUsersName;
  private final GitRepositoryManager gitMgr;

  @Inject
  GetDiffPreferences(
      Provider<CurrentUser> self,
      Provider<AllUsersName> allUsersName,
      GitRepositoryManager gitMgr) {
    this.self = self;
    this.allUsersName = allUsersName;
    this.gitMgr = gitMgr;
  }

  @Override
  public DiffPreferencesInfo apply(AccountResource rsrc)
      throws AuthException, ConfigInvalidException, IOException {
    if (self.get() != rsrc.getUser() && !self.get().getCapabilities().canAdministrateServer()) {
      throw new AuthException("restricted to administrator");
    }

    Account.Id id = rsrc.getUser().getAccountId();
    return readFromGit(id, gitMgr, allUsersName.get(), null);
  }

  static DiffPreferencesInfo readFromGit(
      Account.Id id, GitRepositoryManager gitMgr, AllUsersName allUsersName, DiffPreferencesInfo in)
      throws IOException, ConfigInvalidException, RepositoryNotFoundException {
    try (Repository git = gitMgr.openRepository(allUsersName)) {
      // Load all users prefs.
      VersionedAccountPreferences dp = VersionedAccountPreferences.forDefault();
      dp.load(git);
      DiffPreferencesInfo allUserPrefs = new DiffPreferencesInfo();
      loadSection(
          dp.getConfig(),
          UserConfigSections.DIFF,
          null,
          allUserPrefs,
          DiffPreferencesInfo.defaults(),
          in);

      // Load user prefs
      VersionedAccountPreferences p = VersionedAccountPreferences.forUser(id);
      p.load(git);
      DiffPreferencesInfo prefs = new DiffPreferencesInfo();
      loadSection(
          p.getConfig(), UserConfigSections.DIFF, null, prefs, updateDefaults(allUserPrefs), in);
      return prefs;
    }
  }

  private static DiffPreferencesInfo updateDefaults(DiffPreferencesInfo input) {
    DiffPreferencesInfo result = DiffPreferencesInfo.defaults();
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
      log.warn("Cannot get default diff preferences from All-Users", e);
      return DiffPreferencesInfo.defaults();
    }
    return result;
  }
}
