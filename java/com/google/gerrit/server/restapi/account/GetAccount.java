// Copyright (C) 2013 The Android Open Source Project
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

import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.AccountLoader;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;


/**
 * REST endpoint to get an account.
 *
 * <p>This REST endpoint handles {@code GET /accounts/<account-identifier>} requests.
 *
 * <p>In the response only a subset of fields is populated (see {@link
 * AccountLoader#DETAILED_OPTIONS}). In contrast to this {@link GetDetail} populates all fields in
 * the response.
 */
@Singleton
public class GetAccount implements RestReadView<AccountResource> {
  private final AccountLoader.Factory infoFactory;
  private final Provider<CurrentUser> self;
  private final PermissionBackend permissionBackend;

  @Inject
  GetAccount(Provider<CurrentUser> self, PermissionBackend permissionBackend, AccountLoader.Factory infoFactory) {
    this.infoFactory = infoFactory;
    this.self = self;
    this.permissionBackend = permissionBackend;
  }

  @Override
  public Response<AccountInfo> apply(AccountResource rsrc) throws PermissionBackendException {
    if (!self.get().hasSameAccountId(rsrc.getUser())) {
      permissionBackend.currentUser().check(GlobalPermission.VIEW_ALL_ACCOUNTS);
    }
    AccountLoader loader = infoFactory.create(true);
    AccountInfo info = loader.get(rsrc.getUser().getAccountId());
    loader.fill();
    return Response.ok(info);
  }
}
