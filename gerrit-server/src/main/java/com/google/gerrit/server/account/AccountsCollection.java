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

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AcceptsCreate;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestCollection;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

@Singleton
public class AccountsCollection implements
    RestCollection<TopLevelResource, AccountResource>,
    AcceptsCreate<TopLevelResource> {
  private final Provider<CurrentUser> self;
  private final AccountResolver resolver;
  private final AccountControl.Factory accountControlFactory;
  private final IdentifiedUser.GenericFactory userFactory;
  private final Provider<QueryAccounts> list;
  private final DynamicMap<RestView<AccountResource>> views;
  private final CreateAccount.Factory createAccountFactory;

  @Inject
  AccountsCollection(Provider<CurrentUser> self,
      AccountResolver resolver,
      AccountControl.Factory accountControlFactory,
      IdentifiedUser.GenericFactory userFactory,
      Provider<QueryAccounts> list,
      DynamicMap<RestView<AccountResource>> views,
      CreateAccount.Factory createAccountFactory) {
    this.self = self;
    this.resolver = resolver;
    this.accountControlFactory = accountControlFactory;
    this.userFactory = userFactory;
    this.list = list;
    this.views = views;
    this.createAccountFactory = createAccountFactory;
  }

  @Override
  public AccountResource parse(TopLevelResource root, IdString id)
      throws ResourceNotFoundException, AuthException, OrmException {
    IdentifiedUser user = parseId(id.get());
    if (user == null) {
      throw new ResourceNotFoundException(id);
    } else if (!accountControlFactory.get().canSee(user.getAccount())) {
      throw new ResourceNotFoundException(id);
    }
    return new AccountResource(user);
  }

  /**
   * Parses a account ID from a request body and returns the user.
   *
   * @param id ID of the account, can be a string of the format
   *        "{@code Full Name <email@example.com>}", just the email address,
   *        a full name if it is unique, an account ID, a user name or
   *        "{@code self}" for the calling user
   * @return the user, never null.
   * @throws UnprocessableEntityException thrown if the account ID cannot be
   *         resolved or if the account is not visible to the calling user
   */
  public IdentifiedUser parse(String id) throws AuthException,
      UnprocessableEntityException, OrmException {
    IdentifiedUser user = parseId(id);
    if (user == null) {
      throw new UnprocessableEntityException(String.format(
          "Account Not Found: %s", id));
    } else if (!accountControlFactory.get().canSee(user.getAccount())) {
      throw new UnprocessableEntityException(String.format(
          "Account Not Found: %s", id));
    }
    return user;
  }

  /**
   * Parses an account ID and returns the user without making any permission
   * check whether the current user can see the account.
   *
   * @param id ID of the account, can be a string of the format
   *        "{@code Full Name <email@example.com>}", just the email address,
   *        a full name if it is unique, an account ID, a user name or
   *        "{@code self}" for the calling user
   * @return the user, null if no user is found for the given account ID
   * @throws AuthException thrown if 'self' is used as account ID and the
   *         current user is not authenticated
   * @throws OrmException
   */
  public IdentifiedUser parseId(String id) throws AuthException, OrmException {
    if (id.equals("self")) {
      CurrentUser user = self.get();
      if (user.isIdentifiedUser()) {
        return user.asIdentifiedUser();
      } else if (user instanceof AnonymousUser) {
        throw new AuthException("Authentication required");
      } else {
        return null;
      }
    }

    Account match = resolver.find(id);
    if (match == null) {
      return null;
    }
    return userFactory.create(match.getId());
  }

  @Override
  public RestView<TopLevelResource> list() throws ResourceNotFoundException {
    return list.get();
  }

  @Override
  public DynamicMap<RestView<AccountResource>> views() {
    return views;
  }

  @SuppressWarnings("unchecked")
  @Override
  public CreateAccount create(TopLevelResource parent, IdString username) {
    return createAccountFactory.create(username.get());
  }
}
