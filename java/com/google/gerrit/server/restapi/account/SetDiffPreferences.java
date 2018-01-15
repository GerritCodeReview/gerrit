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

import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.account.AccountsUpdate;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;

@Singleton
public class SetDiffPreferences implements RestModifyView<AccountResource, DiffPreferencesInfo> {
  private final Provider<CurrentUser> self;
  private final PermissionBackend permissionBackend;
  private final AccountCache accountCache;
  private final AccountsUpdate.User accountsUpdate;

  @Inject
  SetDiffPreferences(
      Provider<CurrentUser> self,
      PermissionBackend permissionBackend,
      AccountCache accountCache,
      AccountsUpdate.User accountsUpdate) {
    this.self = self;
    this.permissionBackend = permissionBackend;
    this.accountCache = accountCache;
    this.accountsUpdate = accountsUpdate;
  }

  @Override
  public DiffPreferencesInfo apply(AccountResource rsrc, DiffPreferencesInfo input)
      throws AuthException, BadRequestException, ConfigInvalidException,
          RepositoryNotFoundException, IOException, PermissionBackendException, OrmException {
    if (self.get() != rsrc.getUser()) {
      permissionBackend.user(self).check(GlobalPermission.MODIFY_ACCOUNT);
    }

    if (input == null) {
      throw new BadRequestException("input must be provided");
    }

    Account.Id id = rsrc.getUser().getAccountId();
    accountsUpdate
        .create()
        .update("Set Diff Preferences via API", id, u -> u.setDiffPreferences(input));
    return accountCache.get(id).getDiffPreferences();
  }
}
