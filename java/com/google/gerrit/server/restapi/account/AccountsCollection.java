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

package com.google.gerrit.server.restapi.account;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestCollection;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.reviewdb.server.OrmException;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountControl;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.account.AccountResource;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class AccountsCollection implements RestCollection<TopLevelResource, AccountResource> {
  private final AccountResolver accountResolver;
  private final AccountControl.Factory accountControlFactory;
  private final Provider<QueryAccounts> list;
  private final DynamicMap<RestView<AccountResource>> views;

  @Inject
  public AccountsCollection(
      AccountResolver accountResolver,
      AccountControl.Factory accountControlFactory,
      Provider<QueryAccounts> list,
      DynamicMap<RestView<AccountResource>> views) {
    this.accountResolver = accountResolver;
    this.accountControlFactory = accountControlFactory;
    this.list = list;
    this.views = views;
  }

  @Override
  public AccountResource parse(TopLevelResource root, IdString id)
      throws ResourceNotFoundException, AuthException, OrmException, IOException,
          ConfigInvalidException {
    IdentifiedUser user = accountResolver.parseId(id.get());
    if (user == null || !accountControlFactory.get().canSee(user.getAccount())) {
      throw new ResourceNotFoundException(
          String.format("Account '%s' is not found or ambiguous", id));
    }
    return new AccountResource(user);
  }

  @Override
  public RestView<TopLevelResource> list() throws ResourceNotFoundException {
    return list.get();
  }

  @Override
  public DynamicMap<RestView<AccountResource>> views() {
    return views;
  }
}
