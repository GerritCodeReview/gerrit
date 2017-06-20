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

import static java.util.stream.Collectors.toSet;

import com.google.gerrit.extensions.client.AccountFieldName;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.DeleteEmail.Input;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Set;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class DeleteEmail implements RestModifyView<AccountResource.Email, Input> {
  public static class Input {}

  private final Provider<CurrentUser> self;
  private final Realm realm;
  private final PermissionBackend permissionBackend;
  private final AccountManager accountManager;
  private final ExternalIds externalIds;

  @Inject
  DeleteEmail(
      Provider<CurrentUser> self,
      Realm realm,
      PermissionBackend permissionBackend,
      AccountManager accountManager,
      ExternalIds externalIds) {
    this.self = self;
    this.realm = realm;
    this.permissionBackend = permissionBackend;
    this.accountManager = accountManager;
    this.externalIds = externalIds;
  }

  @Override
  public Response<?> apply(AccountResource.Email rsrc, Input input)
      throws AuthException, ResourceNotFoundException, ResourceConflictException,
          MethodNotAllowedException, OrmException, IOException, ConfigInvalidException,
          PermissionBackendException {
    if (self.get() != rsrc.getUser()) {
      permissionBackend.user(self).check(GlobalPermission.MODIFY_ACCOUNT);
    }
    return apply(rsrc.getUser(), rsrc.getEmail());
  }

  public Response<?> apply(IdentifiedUser user, String email)
      throws ResourceNotFoundException, ResourceConflictException, MethodNotAllowedException,
          OrmException, IOException, ConfigInvalidException {
    if (!realm.allowsEdit(AccountFieldName.REGISTER_NEW_EMAIL)) {
      throw new MethodNotAllowedException("realm does not allow deleting emails");
    }

    Set<ExternalId> extIds =
        externalIds
            .byAccount(user.getAccountId())
            .stream()
            .filter(e -> email.equals(e.email()))
            .collect(toSet());
    if (extIds.isEmpty()) {
      throw new ResourceNotFoundException(email);
    }

    try {
      accountManager.unlink(
          user.getAccountId(), extIds.stream().map(e -> e.key()).collect(toSet()));
    } catch (AccountException e) {
      throw new ResourceConflictException(e.getMessage());
    }
    return Response.none();
  }
}
