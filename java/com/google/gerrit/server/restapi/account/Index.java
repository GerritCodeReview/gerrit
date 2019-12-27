// Copyright (C) 2016 The Android Open Source Project
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

import com.google.gerrit.extensions.common.Input;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.index.account.AccountIndexer;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;

/**
 * REST endpoint to (re)index an account.
 *
 * <p>This REST endpoint handles {@code POST /accounts/<account-identifier>/index} requests.
 *
 * <p>If the document of an account in the account index is stale, this REST endpoint can be used to
 * update the index.
 */
@Singleton
public class Index implements RestModifyView<AccountResource, Input> {

  private final Provider<AccountIndexer> accountIndexer;
  private final PermissionBackend permissionBackend;
  private final Provider<CurrentUser> self;

  @Inject
  Index(
      Provider<AccountIndexer> accountIndexer,
      PermissionBackend permissionBackend,
      Provider<CurrentUser> self) {
    this.accountIndexer = accountIndexer;
    this.permissionBackend = permissionBackend;
    this.self = self;
  }

  @Override
  public Response<?> apply(AccountResource rsrc, Input input)
      throws IOException, AuthException, PermissionBackendException {
    if (!self.get().hasSameAccountId(rsrc.getUser())) {
      permissionBackend.currentUser().check(GlobalPermission.MODIFY_ACCOUNT);
    }

    accountIndexer.get().index(rsrc.getUser().getAccountId());
    return Response.none();
  }
}
