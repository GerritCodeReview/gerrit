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

import com.google.gerrit.extensions.common.Input;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.account.VersionedAuthorizedKeys;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.ssh.SshKeyCache;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;

@Singleton
public class DeleteSshKey implements RestModifyView<AccountResource.SshKey, Input> {

  private final Provider<CurrentUser> self;
  private final PermissionBackend permissionBackend;
  private final VersionedAuthorizedKeys.Accessor authorizedKeys;
  private final SshKeyCache sshKeyCache;

  @Inject
  DeleteSshKey(
      Provider<CurrentUser> self,
      PermissionBackend permissionBackend,
      VersionedAuthorizedKeys.Accessor authorizedKeys,
      SshKeyCache sshKeyCache) {
    this.self = self;
    this.permissionBackend = permissionBackend;
    this.authorizedKeys = authorizedKeys;
    this.sshKeyCache = sshKeyCache;
  }

  @Override
  public Response<?> apply(AccountResource.SshKey rsrc, Input input)
      throws AuthException, OrmException, RepositoryNotFoundException, IOException,
          ConfigInvalidException, PermissionBackendException {
    if (self.get() != rsrc.getUser()) {
      permissionBackend.currentUser().check(GlobalPermission.ADMINISTRATE_SERVER);
    }

    authorizedKeys.deleteKey(rsrc.getUser().getAccountId(), rsrc.getSshKey().seq());
    rsrc.getUser().getUserName().ifPresent(sshKeyCache::evict);

    return Response.none();
  }
}
