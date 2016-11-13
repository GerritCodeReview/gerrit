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

import static com.google.gerrit.server.account.GetEditPreferences.readFromGit;
import static com.google.gerrit.server.config.ConfigUtil.loadSection;
import static com.google.gerrit.server.config.ConfigUtil.storeSection;

import com.google.gerrit.extensions.client.EditPreferencesInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.UserConfigSections;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;

@Singleton
public class SetEditPreferences implements RestModifyView<AccountResource, EditPreferencesInfo> {

  private final Provider<CurrentUser> self;
  private final Provider<MetaDataUpdate.User> metaDataUpdateFactory;
  private final GitRepositoryManager gitMgr;
  private final AllUsersName allUsersName;

  @Inject
  SetEditPreferences(
      Provider<CurrentUser> self,
      Provider<MetaDataUpdate.User> metaDataUpdateFactory,
      GitRepositoryManager gitMgr,
      AllUsersName allUsersName) {
    this.self = self;
    this.metaDataUpdateFactory = metaDataUpdateFactory;
    this.gitMgr = gitMgr;
    this.allUsersName = allUsersName;
  }

  @Override
  public EditPreferencesInfo apply(AccountResource rsrc, EditPreferencesInfo in)
      throws AuthException, BadRequestException, RepositoryNotFoundException, IOException,
          ConfigInvalidException {
    if (self.get() != rsrc.getUser() && !self.get().getCapabilities().canModifyAccount()) {
      throw new AuthException("requires Modify Account capability");
    }

    if (in == null) {
      throw new BadRequestException("input must be provided");
    }

    Account.Id accountId = rsrc.getUser().getAccountId();

    VersionedAccountPreferences prefs;
    EditPreferencesInfo out = new EditPreferencesInfo();
    try (MetaDataUpdate md = metaDataUpdateFactory.get().create(allUsersName)) {
      prefs = VersionedAccountPreferences.forUser(accountId);
      prefs.load(md);
      storeSection(
          prefs.getConfig(),
          UserConfigSections.EDIT,
          null,
          readFromGit(accountId, gitMgr, allUsersName, in),
          EditPreferencesInfo.defaults());
      prefs.commit(md);
      out =
          loadSection(
              prefs.getConfig(),
              UserConfigSections.EDIT,
              null,
              out,
              EditPreferencesInfo.defaults(),
              null);
    }

    return out;
  }
}
