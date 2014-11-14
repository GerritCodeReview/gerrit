// Copyright (C) 2014 The Android Open Source Project
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

import com.google.gerrit.extensions.common.EditPreferencesInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;

@Singleton
public class GetEditPreferences implements RestReadView<AccountResource> {
  private final Provider<CurrentUser> self;
  private final AllUsersName allUsersName;
  private final GitRepositoryManager gitMgr;

  @Inject
  GetEditPreferences(Provider<CurrentUser> self,
      AllUsersName allUsersName,
      GitRepositoryManager gitMgr) {
    this.self = self;
    this.allUsersName = allUsersName;
    this.gitMgr = gitMgr;
  }

  @Override
  public EditPreferencesInfo apply(AccountResource rsrc) throws AuthException,
      IOException, ConfigInvalidException {
    if (self.get() != rsrc.getUser()
        && !self.get().getCapabilities().canAdministrateServer()) {
      throw new AuthException("restricted to administrator");
    }

    Repository git = gitMgr.openRepository(allUsersName);
    try {
      VersionedAccountPreferences p =
          VersionedAccountPreferences.forUser(rsrc.getUser().getAccountId());
      p.load(git);
      EditPreferencesInfo prefs = new EditPreferencesInfo();
      loadSection(p.getConfig(), "edit", null, prefs,
          EditPreferencesInfo.defaults());
      return prefs;
    } finally {
      git.close();
    }
  }
}