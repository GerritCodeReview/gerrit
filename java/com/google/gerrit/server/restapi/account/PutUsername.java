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

package com.google.gerrit.server.restapi.account;

import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_USERNAME;

import com.google.common.base.Strings;
import com.google.gerrit.extensions.api.accounts.UsernameInput;
import com.google.gerrit.extensions.client.AccountFieldName;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.ServerInitiated;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.account.AccountsUpdate;
import com.google.gerrit.server.account.Realm;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.ssh.SshKeyCache;
import com.google.gwtorm.server.OrmDuplicateKeyException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class PutUsername implements RestModifyView<AccountResource, UsernameInput> {
  private final Provider<CurrentUser> self;
  private final PermissionBackend permissionBackend;
  private final ExternalIds externalIds;
  private final Provider<AccountsUpdate> accountsUpdateProvider;
  private final SshKeyCache sshKeyCache;
  private final Realm realm;

  @Inject
  PutUsername(
      Provider<CurrentUser> self,
      PermissionBackend permissionBackend,
      ExternalIds externalIds,
      @ServerInitiated Provider<AccountsUpdate> accountsUpdateProvider,
      SshKeyCache sshKeyCache,
      Realm realm) {
    this.self = self;
    this.permissionBackend = permissionBackend;
    this.externalIds = externalIds;
    this.accountsUpdateProvider = accountsUpdateProvider;
    this.sshKeyCache = sshKeyCache;
    this.realm = realm;
  }

  @Override
  public String apply(AccountResource rsrc, UsernameInput input)
      throws AuthException, MethodNotAllowedException, UnprocessableEntityException,
          ResourceConflictException, OrmException, IOException, ConfigInvalidException,
          PermissionBackendException {
    if (self.get() != rsrc.getUser()) {
      permissionBackend.currentUser().check(GlobalPermission.ADMINISTRATE_SERVER);
    }

    if (!realm.allowsEdit(AccountFieldName.USER_NAME)) {
      throw new MethodNotAllowedException("realm does not allow editing username");
    }

    if (input == null) {
      input = new UsernameInput();
    }

    Account.Id accountId = rsrc.getUser().getAccountId();
    if (!externalIds.byAccount(accountId, SCHEME_USERNAME).isEmpty()) {
      throw new MethodNotAllowedException("Username cannot be changed.");
    }

    if (Strings.isNullOrEmpty(input.username)) {
      return input.username;
    }

    if (!ExternalId.isValidUsername(input.username)) {
      throw new UnprocessableEntityException("invalid username");
    }

    ExternalId.Key key = ExternalId.Key.create(SCHEME_USERNAME, input.username);
    try {
      accountsUpdateProvider
          .get()
          .update(
              "Set Username via API",
              accountId,
              u -> u.addExternalId(ExternalId.create(key, accountId, null, null)));
    } catch (OrmDuplicateKeyException dupeErr) {
      // If we are using this identity, don't report the exception.
      Optional<ExternalId> other = externalIds.get(key);
      if (other.isPresent() && other.get().accountId().equals(accountId)) {
        return input.username;
      }

      // Otherwise, someone else has this identity.
      throw new ResourceConflictException("username already used");
    }

    sshKeyCache.evict(input.username);
    return input.username;
  }
}
