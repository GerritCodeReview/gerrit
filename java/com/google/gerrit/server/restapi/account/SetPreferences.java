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

import com.google.common.base.Strings;
import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo;
import com.google.gerrit.extensions.config.DownloadScheme;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.registration.Extension;
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
import com.google.gerrit.server.account.StoredPreferences;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;

/**
 * REST endpoint to set general preferences for an account.
 *
 * <p>This REST endpoint handles {@code PUT /accounts/<account-identifier>/preferences} requests.
 *
 * <p>Diff preferences can be set by {@link SetDiffPreferences} and edit preferences can be set by
 * {@link SetEditPreferences}.
 *
 * <p>Default general preferences that apply for all accounts can be set by {@link
 * com.google.gerrit.server.restapi.config.SetPreferences}.
 */
@Singleton
public class SetPreferences implements RestModifyView<AccountResource, GeneralPreferencesInfo> {
  private final Provider<CurrentUser> self;
  private final PermissionBackend permissionBackend;
  private final Provider<AccountsUpdate> accountsUpdateProvider;
  private final DynamicMap<DownloadScheme> downloadSchemes;

  @Inject
  SetPreferences(
      Provider<CurrentUser> self,
      PermissionBackend permissionBackend,
      @UserInitiated Provider<AccountsUpdate> accountsUpdateProvider,
      DynamicMap<DownloadScheme> downloadSchemes) {
    this.self = self;
    this.permissionBackend = permissionBackend;
    this.accountsUpdateProvider = accountsUpdateProvider;
    this.downloadSchemes = downloadSchemes;
  }

  @Override
  public Response<GeneralPreferencesInfo> apply(AccountResource rsrc, GeneralPreferencesInfo input)
      throws RestApiException, IOException, ConfigInvalidException, PermissionBackendException {
    if (!self.get().hasSameAccountId(rsrc.getUser())) {
      permissionBackend.currentUser().check(GlobalPermission.MODIFY_ACCOUNT);
    }

    checkDownloadScheme(input.downloadScheme);
    StoredPreferences.validateMy(input.my);
    Account.Id id = rsrc.getUser().getAccountId();

    return Response.ok(
        accountsUpdateProvider
            .get()
            .update("Set General Preferences via API", id, u -> u.setGeneralPreferences(input))
            .map(AccountState::generalPreferences)
            .orElseThrow(() -> new ResourceNotFoundException(IdString.fromDecoded(id.toString()))));
  }

  private void checkDownloadScheme(String downloadScheme) throws BadRequestException {
    if (Strings.isNullOrEmpty(downloadScheme)) {
      return;
    }

    for (Extension<DownloadScheme> e : downloadSchemes) {
      if (e.getExportName().equals(downloadScheme) && e.getProvider().get().isEnabled()) {
        return;
      }
    }
    throw new BadRequestException("Unsupported download scheme: " + downloadScheme);
  }
}
