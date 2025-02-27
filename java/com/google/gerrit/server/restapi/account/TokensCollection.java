// Copyright (C) 2025 The Android Open Source Project
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
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.account.AuthToken;
import com.google.gerrit.server.account.AuthTokenAccessor;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class TokensCollection implements ChildCollection<AccountResource, AccountResource.Token> {
  private final DynamicMap<RestView<AccountResource.Token>> views;
  private final GetTokens list;
  private final Provider<CurrentUser> self;
  private final PermissionBackend permissionBackend;
  private final AuthTokenAccessor tokenAccessor;

  @Inject
  TokensCollection(
      DynamicMap<RestView<AccountResource.Token>> views,
      GetTokens list,
      Provider<CurrentUser> self,
      PermissionBackend permissionBackend,
      AuthTokenAccessor tokenAccessor) {
    this.views = views;
    this.list = list;
    this.self = self;
    this.permissionBackend = permissionBackend;
    this.tokenAccessor = tokenAccessor;
  }

  @Override
  public RestView<AccountResource> list() {
    return list;
  }

  @Override
  public AccountResource.Token parse(AccountResource rsrc, IdString id)
      throws ResourceNotFoundException,
          PermissionBackendException,
          AuthException,
          IOException,
          ConfigInvalidException {
    if (!self.get().hasSameAccountId(rsrc.getUser())) {
      permissionBackend.currentUser().check(GlobalPermission.ADMINISTRATE_SERVER);
    }

    for (AuthToken token : tokenAccessor.getTokens(rsrc.getUser().getAccountId())) {
      if (token.id().equals(id.get())) {
        return new AccountResource.Token(rsrc.getUser(), id.get());
      }
    }

    throw new ResourceNotFoundException(id);
  }

  @Override
  public DynamicMap<RestView<AccountResource.Token>> views() {
    return views;
  }
}
