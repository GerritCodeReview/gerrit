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

package com.google.gerrit.server.account;

import com.google.common.base.Strings;
import com.google.gerrit.extensions.client.AccountFieldName;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.DefaultInput;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.PutName.Input;
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
public class PutName implements RestModifyView<AccountResource, Input> {
  public static class Input {
    @DefaultInput public String name;
  }

  private final Provider<CurrentUser> self;
  private final Realm realm;
  private final PermissionBackend permissionBackend;
  private final Provider<ReviewDb> dbProvider;
  private final AccountsUpdate.Server accountsUpdate;

  @Inject
  PutName(
      Provider<CurrentUser> self,
      Realm realm,
      PermissionBackend permissionBackend,
      Provider<ReviewDb> dbProvider,
      AccountsUpdate.Server accountsUpdate) {
    this.self = self;
    this.realm = realm;
    this.permissionBackend = permissionBackend;
    this.dbProvider = dbProvider;
    this.accountsUpdate = accountsUpdate;
  }

  @Override
  public Response<String> apply(AccountResource rsrc, Input input)
      throws AuthException, MethodNotAllowedException, ResourceNotFoundException, OrmException,
          IOException, PermissionBackendException, ConfigInvalidException {
    if (self.get() != rsrc.getUser()) {
      permissionBackend.user(self).check(GlobalPermission.MODIFY_ACCOUNT);
    }
    return apply(rsrc.getUser(), input);
  }

  public Response<String> apply(IdentifiedUser user, Input input)
      throws MethodNotAllowedException, ResourceNotFoundException, OrmException, IOException,
          ConfigInvalidException {
    if (input == null) {
      input = new Input();
    }

    if (!realm.allowsEdit(AccountFieldName.FULL_NAME)) {
      throw new MethodNotAllowedException("realm does not allow editing name");
    }

    String newName = input.name;
    Account account =
        accountsUpdate
            .create()
            .update(dbProvider.get(), user.getAccountId(), a -> a.setFullName(newName));
    if (account == null) {
      throw new ResourceNotFoundException("account not found");
    }
    return Strings.isNullOrEmpty(account.getFullName())
        ? Response.none()
        : Response.ok(account.getFullName());
  }
}
