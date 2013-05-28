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

import static com.google.gerrit.reviewdb.client.AccountExternalId.SCHEME_USERNAME;

import com.google.common.base.Strings;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.AccountExternalId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

public class GetHttpPassword implements RestReadView<AccountResource> {

  private final Provider<CurrentUser> self;
  private final Provider<ReviewDb> dbProvider;

  @Inject
  GetHttpPassword(Provider<CurrentUser> self, Provider<ReviewDb> dbProvider) {
    this.self = self;
    this.dbProvider = dbProvider;
  }

  @Override
  public String apply(AccountResource rsrc) throws AuthException,
      ResourceNotFoundException, OrmException {
    if (self.get() != rsrc.getUser()
        && !self.get().getCapabilities().canAdministrateServer()) {
      throw new AuthException("not allowed to get http password");
    }
    AccountExternalId id =
        dbProvider.get().accountExternalIds().get(
          new AccountExternalId.Key(SCHEME_USERNAME, rsrc.getUser().getUserName()));
    if (id == null) {
      throw new ResourceNotFoundException();
    }
    return Strings.nullToEmpty(id.getPassword());
  }
}
