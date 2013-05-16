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

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountResource.Email;
import com.google.inject.Inject;
import com.google.inject.Provider;

public class Emails implements
    ChildCollection<AccountResource, AccountResource.Email> {
  private final DynamicMap<RestView<AccountResource.Email>> views;
  private final Provider<GetEmails> get;
  private final AccountByEmailCache byEmailCache;
  private final Provider<CurrentUser> self;

  @Inject
  Emails(DynamicMap<RestView<AccountResource.Email>> views,
      Provider<GetEmails> get, AccountByEmailCache byEmailCache,
      Provider<CurrentUser> self) {
    this.views = views;
    this.get = get;
    this.byEmailCache = byEmailCache;
    this.self = self;
  }

  @Override
  public RestView<AccountResource> list() {
    return get.get();
  }

  @Override
  public AccountResource.Email parse(AccountResource parent, IdString id)
      throws AuthException, ResourceNotFoundException {
    if (!(self.get() instanceof IdentifiedUser)) {
      throw new AuthException("Authentication required");
    }
    IdentifiedUser s = (IdentifiedUser) self.get();
    if (s.getAccountId().get() == parent.getUser().getAccountId().get()
        || s.getCapabilities().canAdministrateServer()) {
      for (Account.Id a : byEmailCache.get(id.get())) {
        if (parent.getUser().getAccountId().equals(a)) {
          return new AccountResource.Email(parent.getUser(), id.get());
        }
      }
    }
    throw new ResourceNotFoundException();
  }

  @Override
  public DynamicMap<RestView<Email>> views() {
    return views;
  }
}
