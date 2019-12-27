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

import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;

/**
 * REST endpoint to get the diff preferences of an account.
 *
 * <p>This REST endpoint handles {@code GET /accounts/<account-identifier>/preferences.diff}
 * requests.
 *
 * <p>General preferences can be retrieved by {@link GetPreferences} and edit preferences can be
 * retrieved by {@link GetEditPreferences}.
 *
 * <p>Default diff preferences that apply for all accounts can be retrieved by {@link
 * com.google.gerrit.server.restapi.config.GetDiffPreferences}.
 */
@Singleton
public class GetDiffPreferences implements RestReadView<AccountResource> {
  private final Provider<CurrentUser> self;
  private final PermissionBackend permissionBackend;
  private final AccountCache accountCache;

  @Inject
  GetDiffPreferences(
      Provider<CurrentUser> self, PermissionBackend permissionBackend, AccountCache accountCache) {
    this.self = self;
    this.permissionBackend = permissionBackend;
    this.accountCache = accountCache;
  }

  @Override
  public Response<DiffPreferencesInfo> apply(AccountResource rsrc)
      throws RestApiException, ConfigInvalidException, IOException, PermissionBackendException {
    if (!self.get().hasSameAccountId(rsrc.getUser())) {
      permissionBackend.currentUser().check(GlobalPermission.ADMINISTRATE_SERVER);
    }

    Account.Id id = rsrc.getUser().getAccountId();
    return Response.ok(
        accountCache
            .get(id)
            .map(AccountState::diffPreferences)
            .orElseThrow(() -> new ResourceNotFoundException(IdString.fromDecoded(id.toString()))));
  }
}
