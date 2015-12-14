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

package com.google.gerrit.server.api.accounts;

import com.google.gerrit.extensions.api.accounts.AccountApi;
import com.google.gerrit.extensions.api.accounts.Accounts;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.account.AccountsCollection;
import com.google.gerrit.server.account.SuggestAccounts;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import java.util.List;

@Singleton
public class AccountsImpl implements Accounts {
  private final AccountsCollection accounts;
  private final AccountApiImpl.Factory api;
  private final Provider<CurrentUser> self;
  private final Provider<SuggestAccounts> suggestAccountsProvider;

  @Inject
  AccountsImpl(AccountsCollection accounts,
      AccountApiImpl.Factory api,
      Provider<CurrentUser> self,
      Provider<SuggestAccounts> suggestAccountsProvider) {
    this.accounts = accounts;
    this.api = api;
    this.self = self;
    this.suggestAccountsProvider = suggestAccountsProvider;
  }

  @Override
  public AccountApi id(String id) throws RestApiException {
    try {
      return api.create(accounts.parse(TopLevelResource.INSTANCE,
          IdString.fromDecoded(id)));
    } catch (OrmException e) {
      throw new RestApiException("Cannot parse change", e);
    }
  }

  @Override
  public AccountApi id(int id) throws RestApiException {
    return id(String.valueOf(id));
  }

  @Override
  public AccountApi self() throws RestApiException {
    if (!self.get().isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }
    return api.create(new AccountResource(self.get().asIdentifiedUser()));
  }

  @Override
  public SuggestAccountsRequest suggestAccounts() throws RestApiException {
    return new SuggestAccountsRequest() {
      @Override
      public List<AccountInfo> get() throws RestApiException {
        return AccountsImpl.this.suggestAccounts(this);
      }
    };
  }

  @Override
  public SuggestAccountsRequest suggestAccounts(String query)
    throws RestApiException {
    return suggestAccounts().withQuery(query);
  }

  private List<AccountInfo> suggestAccounts(SuggestAccountsRequest r)
    throws RestApiException {
    try {
      SuggestAccounts mySuggestAccounts = suggestAccountsProvider.get();
      mySuggestAccounts.setQuery(r.getQuery());
      mySuggestAccounts.setLimit(r.getLimit());
      return mySuggestAccounts.apply(TopLevelResource.INSTANCE);
    } catch (OrmException e) {
      throw new RestApiException("Cannot retrieve suggested accounts", e);
    }
  }
}
