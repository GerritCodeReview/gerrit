// Copyright (C) 2015 The Android Open Source Project
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

import com.google.gerrit.common.errors.NameAlreadyUsedException;
import com.google.gerrit.extensions.api.accounts.UsernameInput;
import com.google.gerrit.extensions.client.AccountFieldName;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.server.CurrentUser;
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
public class PutUsername implements RestModifyView<AccountResource, UsernameInput> {
  private final Provider<CurrentUser> self;
  private final ChangeUserName.Factory changeUserNameFactory;
  private final PermissionBackend permissionBackend;
  private final Realm realm;

  @Inject
  PutUsername(
      Provider<CurrentUser> self,
      ChangeUserName.Factory changeUserNameFactory,
      PermissionBackend permissionBackend,
      Realm realm) {
    this.self = self;
    this.changeUserNameFactory = changeUserNameFactory;
    this.permissionBackend = permissionBackend;
    this.realm = realm;
  }

  @Override
  public String apply(AccountResource rsrc, UsernameInput input)
      throws AuthException, MethodNotAllowedException, UnprocessableEntityException,
          ResourceConflictException, OrmException, IOException, ConfigInvalidException,
          PermissionBackendException {
    if (self.get() != rsrc.getUser()) {
      permissionBackend.user(self).check(GlobalPermission.ADMINISTRATE_SERVER);
    }

    if (!realm.allowsEdit(AccountFieldName.USER_NAME)) {
      throw new MethodNotAllowedException("realm does not allow editing username");
    }

    if (input == null) {
      input = new UsernameInput();
    }

    try {
      changeUserNameFactory.create(rsrc.getUser(), input.username).call();
    } catch (IllegalStateException e) {
      if (ChangeUserName.USERNAME_CANNOT_BE_CHANGED.equals(e.getMessage())) {
        throw new MethodNotAllowedException(e.getMessage());
      }
      throw e;
    } catch (InvalidUserNameException e) {
      throw new UnprocessableEntityException("invalid username");
    } catch (NameAlreadyUsedException e) {
      throw new ResourceConflictException("username already used");
    }

    return input.username;
  }
}
