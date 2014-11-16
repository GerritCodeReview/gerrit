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
import static com.google.gerrit.server.config.ConfigUtil.storeSection;

import com.google.gerrit.extensions.common.DiffPreferencesInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.MetaDataUpdate;
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
  private final MetaDataUpdate.User metaDataUpdateFactory;
  private final AllUsersName allUsersName;

  @Inject
  SetDiffPreferences(Provider<CurrentUser> self,
      MetaDataUpdate.User metaDataUpdateFactory,
      AllUsersName allUsersName) {
    this.self = self;
    this.metaDataUpdateFactory = metaDataUpdateFactory;
    this.allUsersName = allUsersName;
  }

  @Override
  public DiffPreferencesInfo apply(AccountResource rsrc, DiffPreferencesInfo in)
      throws AuthException, BadRequestException, ConfigInvalidException,
      RepositoryNotFoundException, IOException {
    if (self.get() != rsrc.getUser()
        && !self.get().getCapabilities().canModifyAccount()) {
      throw new AuthException("restricted to members of Modify Accounts");
    }

    if (in == null) {
      throw new BadRequestException("input must be provided");
    }

    Account.Id accountId = rsrc.getUser().getAccountId();
    MetaDataUpdate md = metaDataUpdateFactory.create(allUsersName);

    VersionedAccountPreferences prefs;
    DiffPreferencesInfo out = new DiffPreferencesInfo();
    try {
      prefs = VersionedAccountPreferences.forUser(accountId);
      prefs.load(md);
      storeSection(prefs.getConfig(), "diff", null, in);
      prefs.commit(md);
      loadSection(prefs.getConfig(), "diff", null, out);
    } finally {
      md.close();
    }
    return out;
  }
}
