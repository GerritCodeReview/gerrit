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

import com.google.gerrit.extensions.auth.AuthTokenInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
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
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;

/**
 * REST endpoint to list the tokens of an account.
 *
 * <p>This REST endpoint handles {@code GET /accounts/<account-identifier>/tokens/} requests.
 */
@Singleton
public class GetTokens implements RestReadView<AccountResource> {
  private final Provider<CurrentUser> self;
  private final PermissionBackend permissionBackend;
  private final AuthTokenAccessor tokenAccessor;

  @Inject
  GetTokens(
      Provider<CurrentUser> self,
      PermissionBackend permissionBackend,
      AuthTokenAccessor tokenAccessor) {
    this.self = self;
    this.permissionBackend = permissionBackend;
    this.tokenAccessor = tokenAccessor;
  }

  @Override
  public Response<List<AuthTokenInfo>> apply(AccountResource rsrc)
      throws AuthException,
          PermissionBackendException,
          RepositoryNotFoundException,
          IOException,
          ConfigInvalidException {
    if (!self.get().hasSameAccountId(rsrc.getUser())) {
      permissionBackend.currentUser().check(GlobalPermission.MODIFY_ACCOUNT);
    }
    return Response.ok(apply(rsrc.getUser()));
  }

  public List<AuthTokenInfo> apply(IdentifiedUser user) throws IOException, ConfigInvalidException {
    List<AuthTokenInfo> authTokenInfos = new ArrayList<>();
    for (AuthToken token : tokenAccessor.getTokens(user.getAccountId())) {
      authTokenInfos.add(newTokenInfo(token));
    }
    return authTokenInfos;
  }

  public static AuthTokenInfo newTokenInfo(AuthToken token) {
    AuthTokenInfo info = new AuthTokenInfo();
    info.id = token.id();
    if (token.expirationDate().isPresent()) {
      info.expiration = Timestamp.from(token.expirationDate().get());
    }
    return info;
  }
}
