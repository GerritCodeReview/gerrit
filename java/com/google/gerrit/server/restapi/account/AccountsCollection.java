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
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.account.AccountResolver.UnresolvableAccountException;
import com.google.gerrit.server.account.AccountResource;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class AccountsCollection implements RestCollection<TopLevelResource, AccountResource> {
  private final AccountResolver accountResolver;
  private final Provider<QueryAccounts> list;
  private final DynamicMap<RestView<AccountResource>> views;

  @Inject
  public AccountsCollection(
      AccountResolver accountResolver,
      Provider<QueryAccounts> list,
      DynamicMap<RestView<AccountResource>> views) {
    this.accountResolver = accountResolver;
    this.list = list;
    this.views = views;
  }

  @Override
  public AccountResource parse(TopLevelResource root, IdString id)
      throws ResourceNotFoundException, AuthException, IOException, ConfigInvalidException {
    try {
      return new AccountResource(accountResolver.resolve(id.get()).asUniqueUser());
    } catch (UnresolvableAccountException e) {
      if (e.isSelf()) {
        throw new AuthException(e.getMessage(), e);
      }
      throw new ResourceNotFoundException(e.getMessage(), e);
    }
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
