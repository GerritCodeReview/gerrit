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

import static com.google.gerrit.server.api.ApiUtil.asRestApiException;
import static java.util.Objects.requireNonNull;

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
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.restapi.account.AccountsCollection;
import com.google.gerrit.server.restapi.account.CreateAccount;
import com.google.gerrit.server.restapi.account.DeleteAccount;
import com.google.gerrit.server.restapi.account.QueryAccounts;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.List;

@Singleton
public class AccountsImpl implements Accounts {
  private final AccountsCollection accounts;
  private final AccountApiImpl.Factory api;
  private final PermissionBackend permissionBackend;
  private final Provider<CurrentUser> self;
  private final CreateAccount createAccount;
  private final DeleteAccount deleteAccount;
  private final Provider<QueryAccounts> queryAccountsProvider;

  @Inject
  AccountsImpl(
      AccountsCollection accounts,
      AccountApiImpl.Factory api,
      PermissionBackend permissionBackend,
      Provider<CurrentUser> self,
      CreateAccount createAccount,
      DeleteAccount deleteAccount,
      Provider<QueryAccounts> queryAccountsProvider) {
    this.accounts = accounts;
    this.api = api;
    this.permissionBackend = permissionBackend;
    this.self = self;
    this.createAccount = createAccount;
    this.deleteAccount = deleteAccount;
    this.queryAccountsProvider = queryAccountsProvider;
  }

  @Override
  public AccountApi id(String id) throws RestApiException {
    try {
      return api.create(accounts.parse(TopLevelResource.INSTANCE, IdString.fromDecoded(id)));
    } catch (Exception e) {
      throw asRestApiException("Cannot parse account", e);
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
    if (requireNonNull(in, "AccountInput").username == null) {
      throw new BadRequestException("AccountInput must specify username");
    }
    try {
      permissionBackend
          .currentUser()
          .checkAny(GlobalPermission.fromAnnotation(createAccount.getClass()));
      AccountInfo info =
          createAccount
              .apply(TopLevelResource.INSTANCE, IdString.fromDecoded(in.username), in)
              .value();
      return id(info._accountId);
    } catch (Exception e) {
      throw asRestApiException("Cannot create account " + in.username, e);
    }
  }

  @Override
  public void deleteSelf() throws RestApiException {
    if (!self.get().isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }
    try {
      deleteAccount.apply(new AccountResource(self.get().asIdentifiedUser()), null);
    } catch (Exception e) {
      throw asRestApiException(
          "Cannot delete account " + self.get().asIdentifiedUser().getEmailAddresses(), e);
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
      return myQueryAccounts.apply(TopLevelResource.INSTANCE).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot retrieve suggested accounts", e);
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
      myQueryAccounts.setSuggest(r.getSuggest());
      for (ListAccountsOption option : r.getOptions()) {
        myQueryAccounts.addOption(option);
      }
      return myQueryAccounts.apply(TopLevelResource.INSTANCE).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot retrieve suggested accounts", e);
    }
  }
}
