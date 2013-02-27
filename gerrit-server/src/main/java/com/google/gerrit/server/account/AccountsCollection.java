// Copyright (C) 2012 The Android Open Source Project
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

import com.google.common.collect.Iterables;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestCollection;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.Set;

public class AccountsCollection implements
    RestCollection<TopLevelResource, AccountResource> {
  private final Provider<CurrentUser> self;
  private final AccountResolver resolver;
  private final AccountControl.Factory accountControlFactory;
  private final IdentifiedUser.GenericFactory userFactory;
  private final DynamicMap<RestView<AccountResource>> views;

  @Inject
  AccountsCollection(Provider<CurrentUser> self,
      AccountResolver resolver,
      AccountControl.Factory accountControlFactory,
      IdentifiedUser.GenericFactory userFactory,
      DynamicMap<RestView<AccountResource>> views) {
    this.self = self;
    this.resolver = resolver;
    this.accountControlFactory = accountControlFactory;
    this.userFactory = userFactory;
    this.views = views;
  }

  @Override
  public AccountResource parse(TopLevelResource root, IdString id)
      throws ResourceNotFoundException, AuthException, OrmException {
    return new AccountResource(parse(id.get()));
  }

  public IdentifiedUser parse(String id) throws AuthException,
      ResourceNotFoundException, OrmException {
    CurrentUser user = self.get();

    if (id.equals("self")) {
      if (user instanceof IdentifiedUser) {
        return (IdentifiedUser) user;
      } else if (user instanceof AnonymousUser) {
        throw new AuthException("Authentication required");
      } else {
        throw new ResourceNotFoundException(id);
      }
    }

    Set<Account.Id> matches = resolver.findAll(id);
    if (matches.size() != 1) {
      throw new ResourceNotFoundException(id);
    }

    Account.Id a = Iterables.getOnlyElement(matches);
    if (accountControlFactory.get().canSee(a)
        || user.getCapabilities().canAdministrateServer()) {
      return userFactory.create(a);
    } else {
      throw new ResourceNotFoundException(id);
    }
  }

  @Override
  public RestView<TopLevelResource> list() throws ResourceNotFoundException {
    throw new ResourceNotFoundException();
  }

  @Override
  public DynamicMap<RestView<AccountResource>> views() {
    return views;
  }
}
