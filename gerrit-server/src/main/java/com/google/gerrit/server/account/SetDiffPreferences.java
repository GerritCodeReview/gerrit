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

import static com.google.gerrit.server.account.GetDiffPreferences.readFromGit;
import static com.google.gerrit.server.config.ConfigUtil.loadSection;
import static com.google.gerrit.server.config.ConfigUtil.storeSection;

import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.UserConfigSections;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;

import java.io.IOException;

@Singleton
public class SetDiffPreferences implements
    RestModifyView<AccountResource, DiffPreferencesInfo> {
  private final Provider<CurrentUser> self;
  private final Provider<MetaDataUpdate.User> metaDataUpdateFactory;
  private final AllUsersName allUsersName;
  private final GitRepositoryManager gitMgr;

  @Inject
  SetDiffPreferences(Provider<CurrentUser> self,
      Provider<MetaDataUpdate.User> metaDataUpdateFactory,
      AllUsersName allUsersName,
      GitRepositoryManager gitMgr) {
    this.self = self;
    this.metaDataUpdateFactory = metaDataUpdateFactory;
    this.allUsersName = allUsersName;
    this.gitMgr = gitMgr;
  }

  @Override
  public DiffPreferencesInfo apply(AccountResource rsrc, DiffPreferencesInfo in)
      throws AuthException, BadRequestException, ConfigInvalidException,
      RepositoryNotFoundException, IOException, OrmException {
    if (self.get() != rsrc.getUser()
        && !self.get().getCapabilities().canModifyAccount()) {
      throw new AuthException("restricted to members of Modify Accounts");
    }

    if (in == null) {
      throw new BadRequestException("input must be provided");
    }

    Account.Id id = rsrc.getUser().getAccountId();
    return writeToGit(readFromGit(id, gitMgr, allUsersName, in), id);
  }

  private DiffPreferencesInfo writeToGit(DiffPreferencesInfo in,
      Account.Id userId) throws RepositoryNotFoundException, IOException,
          ConfigInvalidException {
    MetaDataUpdate md = metaDataUpdateFactory.get().create(allUsersName);

    DiffPreferencesInfo out = new DiffPreferencesInfo();
    try {
      VersionedAccountPreferences prefs = VersionedAccountPreferences.forUser(
          userId);
      prefs.load(md);
      DiffPreferencesInfo defaults = DiffPreferencesInfo.defaults();
      storeSection(prefs.getConfig(), UserConfigSections.DIFF, null, in,
          defaults);
      prefs.commit(md);
      loadSection(prefs.getConfig(), UserConfigSections.DIFF, null, out,
          DiffPreferencesInfo.defaults(), null);
    } finally {
      md.close();
    }
    return out;
  }
}
