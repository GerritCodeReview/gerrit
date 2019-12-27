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

package com.google.gerrit.server.restapi.account;

import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.client.EditPreferencesInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.UserInitiated;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.AccountsUpdate;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;

/**
 * REST endpoint to set edit preferences for an account.
 *
 * <p>This REST endpoint handles {@code PUT /accounts/<account-identifier>/preferences.edit}
 * requests.
 *
 * <p>General preferences can be set by {@link SetPreferences} and diff preferences can be set by
 * {@link SetDiffPreferences}.
 *
 * <p>Default edit preferences that apply for all accounts can be set by {@link
 * com.google.gerrit.server.restapi.config.SetEditPreferences}.
 */
@Singleton
public class SetEditPreferences implements RestModifyView<AccountResource, EditPreferencesInfo> {

  private final Provider<CurrentUser> self;
  private final PermissionBackend permissionBackend;
  private final Provider<AccountsUpdate> accountsUpdateProvider;

  @Inject
  SetEditPreferences(
      Provider<CurrentUser> self,
      PermissionBackend permissionBackend,
      @UserInitiated Provider<AccountsUpdate> accountsUpdateProvider) {
    this.self = self;
    this.permissionBackend = permissionBackend;
    this.accountsUpdateProvider = accountsUpdateProvider;
  }

  @Override
  public Response<EditPreferencesInfo> apply(AccountResource rsrc, EditPreferencesInfo input)
      throws RestApiException, RepositoryNotFoundException, IOException, ConfigInvalidException,
          PermissionBackendException {
    if (!self.get().hasSameAccountId(rsrc.getUser())) {
      permissionBackend.currentUser().check(GlobalPermission.MODIFY_ACCOUNT);
    }

    if (input == null) {
      throw new BadRequestException("input must be provided");
    }

    Account.Id id = rsrc.getUser().getAccountId();
    return Response.ok(
        accountsUpdateProvider
            .get()
            .update("Set Edit Preferences via API", id, u -> u.setEditPreferences(input))
            .map(AccountState::editPreferences)
            .orElseThrow(() -> new ResourceNotFoundException(IdString.fromDecoded(id.toString()))));
  }
}
