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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.gerrit.server.account.CapabilityUtils.checkRequiresCapability;

import com.google.gerrit.extensions.api.accounts.AccountApi;
import com.google.gerrit.extensions.api.accounts.AccountInput;
import com.google.gerrit.extensions.api.accounts.Accounts;
import com.google.gerrit.extensions.client.ListAccountsOption;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.account.AccountsCollection;
import com.google.gerrit.server.account.CreateAccount;
import com.google.gerrit.server.account.QueryAccounts;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class AccountsImpl implements Accounts {
  private final AccountsCollection accounts;
  private final AccountApiImpl.Factory api;
  private final Provider<CurrentUser> self;
  private final CreateAccount.Factory createAccount;
  private final Provider<QueryAccounts> queryAccountsProvider;

  @Inject
  AccountsImpl(
      AccountsCollection accounts,
      AccountApiImpl.Factory api,
      Provider<CurrentUser> self,
      CreateAccount.Factory createAccount,
      Provider<QueryAccounts> queryAccountsProvider) {
    this.accounts = accounts;
    this.api = api;
    this.self = self;
    this.createAccount = createAccount;
    this.queryAccountsProvider = queryAccountsProvider;
  }

  @Override
  public AccountApi id(String id) throws RestApiException {
    try {
      return api.create(accounts.parse(TopLevelResource.INSTANCE, IdString.fromDecoded(id)));
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
  public AccountApi create(String username) throws RestApiException {
    AccountInput in = new AccountInput();
    in.username = username;
    return create(in);
  }

  @Override
  public AccountApi create(AccountInput in) throws RestApiException {
    if (checkNotNull(in, "AccountInput").username == null) {
      throw new BadRequestException("AccountInput must specify username");
    }
    checkRequiresCapability(self, null, CreateAccount.class);
    try {
      AccountInfo info =
          createAccount.create(in.username).apply(TopLevelResource.INSTANCE, in).value();
      return id(info._accountId);
    } catch (OrmException | IOException | ConfigInvalidException e) {
      throw new RestApiException("Cannot create account " + in.username, e);
    }
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
  public SuggestAccountsRequest suggestAccounts(String query) throws RestApiException {
    return suggestAccounts().withQuery(query);
  }

  private List<AccountInfo> suggestAccounts(SuggestAccountsRequest r) throws RestApiException {
    try {
      QueryAccounts myQueryAccounts = queryAccountsProvider.get();
      myQueryAccounts.setSuggest(true);
      myQueryAccounts.setQuery(r.getQuery());
      myQueryAccounts.setLimit(r.getLimit());
      return myQueryAccounts.apply(TopLevelResource.INSTANCE);
    } catch (OrmException e) {
      throw new RestApiException("Cannot retrieve suggested accounts", e);
    }
  }

  @Override
  public QueryRequest query() throws RestApiException {
    return new QueryRequest() {
      @Override
      public List<AccountInfo> get() throws RestApiException {
        return AccountsImpl.this.query(this);
      }
    };
  }

  @Override
  public QueryRequest query(String query) throws RestApiException {
    return query().withQuery(query);
  }

  private List<AccountInfo> query(QueryRequest r) throws RestApiException {
    try {
      QueryAccounts myQueryAccounts = queryAccountsProvider.get();
      myQueryAccounts.setQuery(r.getQuery());
      myQueryAccounts.setLimit(r.getLimit());
      myQueryAccounts.setStart(r.getStart());
      for (ListAccountsOption option : r.getOptions()) {
        myQueryAccounts.addOption(option);
      }
      return myQueryAccounts.apply(TopLevelResource.INSTANCE);
    } catch (OrmException e) {
      throw new RestApiException("Cannot retrieve suggested accounts", e);
    }
  }
}
