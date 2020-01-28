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
import com.google.gerrit.extensions.client.AccountFieldName;
import com.google.gerrit.extensions.common.NameInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.ServerInitiated;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.AccountsUpdate;
import com.google.gerrit.server.account.Realm;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class PutName implements RestModifyView<AccountResource, NameInput> {
  private final Provider<CurrentUser> self;
  private final Realm realm;
  private final PermissionBackend permissionBackend;
  private final ExternalIds externalIds;
  private final Provider<AccountsUpdate> accountsUpdateProvider;

  @Inject
  PutName(
      Provider<CurrentUser> self,
      Realm realm,
      PermissionBackend permissionBackend,
      ExternalIds externalIds,
      @ServerInitiated Provider<AccountsUpdate> accountsUpdateProvider) {
    this.self = self;
    this.realm = realm;
    this.permissionBackend = permissionBackend;
    this.externalIds = externalIds;
    this.accountsUpdateProvider = accountsUpdateProvider;
  }

  @Override
  public Response<String> apply(AccountResource rsrc, NameInput input)
      throws AuthException, MethodNotAllowedException, ResourceNotFoundException, OrmException,
          IOException, PermissionBackendException, ConfigInvalidException {
    if (!self.get().hasSameAccountId(rsrc.getUser())) {
      permissionBackend.currentUser().check(GlobalPermission.MODIFY_ACCOUNT);
    }
    return apply(rsrc.getUser(), input);
  }

  public Response<String> apply(IdentifiedUser user, NameInput input)
      throws MethodNotAllowedException, ResourceNotFoundException, IOException,
          ConfigInvalidException, OrmException {

    if (input == null) {
      input = new NameInput();
    }

    Account.Id accountId = user.getAccountId();
    if (realm.accountBelongsToRealm(externalIds.byAccount(accountId))
        && !realm.allowsEdit(AccountFieldName.FULL_NAME)) {
      throw new MethodNotAllowedException("realm does not allow editing name");
    }

    String newName = input.name;
    AccountState accountState =
        accountsUpdateProvider
            .get()
            .update("Set Full Name via API", accountId, u -> u.setFullName(newName))
            .orElseThrow(() -> new ResourceNotFoundException("account not found"));
    return Strings.isNullOrEmpty(accountState.getAccount().getFullName())
        ? Response.none()
        : Response.ok(accountState.getAccount().getFullName());
  }
}
