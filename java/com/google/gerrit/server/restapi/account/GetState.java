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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.common.AccountMetadataInfo;
import com.google.gerrit.extensions.common.AccountStateInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.account.AccountStateProvider;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;

/**
 * REST endpoint to retrieve the superset of all information related to an account. This information
 * is useful to inspect issues with the account and its permissions.
 *
 * <p>Users can only get the own account state. Getting the account state of other users is not
 * allowed.
 */
@Singleton
public class GetState implements RestReadView<AccountResource> {
  private final PermissionBackend permissionBackend;
  private final Provider<CurrentUser> self;
  private final Provider<GetCapabilities> getCapabilities;
  private final GetDetail getDetail;
  private final GetGroups getGroups;
  private final GetExternalIds getExternalIds;
  private final PluginSetContext<AccountStateProvider> accountStateProviders;

  @Inject
  GetState(
      PermissionBackend permissionBackend,
      Provider<CurrentUser> self,
      Provider<GetCapabilities> getCapabilities,
      GetDetail getDetail,
      GetGroups getGroups,
      GetExternalIds getExternalIds,
      PluginSetContext<AccountStateProvider> accountStateProviders) {
    this.permissionBackend = permissionBackend;
    this.self = self;
    this.getCapabilities = getCapabilities;
    this.getDetail = getDetail;
    this.getGroups = getGroups;
    this.getExternalIds = getExternalIds;
    this.accountStateProviders = accountStateProviders;
  }

  @Override
  public Response<AccountStateInfo> apply(AccountResource rsrc)
      throws RestApiException, PermissionBackendException, IOException {
    if (!self.get().isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }

    if (!rsrc.getUser().hasSameAccountId(self.get())) {
      try {
        permissionBackend.currentUser().check(GlobalPermission.ADMINISTRATE_SERVER);
      } catch (AuthException e) {
        throw new AuthException(
            String.format("cannot get account state of other user: %s", e.getMessage()));
      }
    }

    AccountStateInfo accountState = new AccountStateInfo();
    accountState.account = getDetail.apply(rsrc).value();

    if (permissionBackend.usesDefaultCapabilities()) {
      accountState.capabilities = getCapabilities.get().apply(rsrc).value();
    }

    accountState.groups = getGroups.apply(rsrc).value();
    accountState.externalIds = getExternalIds.apply(rsrc).value();
    accountState.metadata = getMetadata(rsrc.getUser().getAccountId());
    return Response.ok(accountState);
  }

  private ImmutableList<AccountMetadataInfo> getMetadata(Account.Id accountId) {
    ArrayList<AccountMetadataInfo> metadataList = new ArrayList<>();
    accountStateProviders.runEach(
        accountStateProvider -> metadataList.addAll(accountStateProvider.getMetadata(accountId)));
    return metadataList.stream()
        .sorted(
            Comparator.comparing((AccountMetadataInfo metadata) -> metadata.name)
                .thenComparing((AccountMetadataInfo metadata) -> metadata.value))
        .collect(toImmutableList());
  }
}
