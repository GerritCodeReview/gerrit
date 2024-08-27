// Copyright (C) 2024 The Android Open Source Project
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

import com.google.gerrit.extensions.common.AccountStateInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;

/**
 * REST endpoint to retrieve the superset of all information related to an account. This information
 * is useful to inspect issues with the account and its permissions.
 *
 * <p>Users can only get the own account state. Getting the account state of other users is not
 * allowed.
 */
@Singleton
public class GetState implements RestReadView<AccountResource> {
  private final Provider<CurrentUser> self;
  private final Provider<GetCapabilities> getCapabilities;
  private final GetDetail getDetail;
  private final GetGroups getGroups;
  private final GetExternalIds getExternalIds;

  @Inject
  GetState(
      Provider<CurrentUser> self,
      Provider<GetCapabilities> getCapabilities,
      GetDetail getDetail,
      GetGroups getGroups,
      GetExternalIds getExternalIds) {
    this.self = self;
    this.getCapabilities = getCapabilities;
    this.getDetail = getDetail;
    this.getGroups = getGroups;
    this.getExternalIds = getExternalIds;
  }

  @Override
  public Response<AccountStateInfo> apply(AccountResource rsrc)
      throws RestApiException, PermissionBackendException, IOException {
    if (!self.get().isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }

    if (!rsrc.getUser().hasSameAccountId(self.get())) {
      throw new AuthException("not allowed to get account state of other user");
    }

    AccountStateInfo accountState = new AccountStateInfo();
    accountState.account = getDetail.apply(rsrc).value();
    accountState.capabilities = getCapabilities.get().apply(rsrc).value();
    accountState.groups = getGroups.apply(rsrc).value();
    accountState.externalIds = getExternalIds.apply(rsrc).value();
    return Response.ok(accountState);
  }
}
