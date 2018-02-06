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
import com.google.common.collect.Lists;
import com.google.gerrit.extensions.common.SshKeyInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.account.AccountSshKey;
import com.google.gerrit.server.account.VersionedAuthorizedKeys;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;

@Singleton
public class GetSshKeys implements RestReadView<AccountResource> {

  private final Provider<CurrentUser> self;
  private final PermissionBackend permissionBackend;
  private final VersionedAuthorizedKeys.Accessor authorizedKeys;

  @Inject
  GetSshKeys(
      Provider<CurrentUser> self,
      PermissionBackend permissionBackend,
      VersionedAuthorizedKeys.Accessor authorizedKeys) {
    this.self = self;
    this.permissionBackend = permissionBackend;
    this.authorizedKeys = authorizedKeys;
  }

  @Override
  public List<SshKeyInfo> apply(AccountResource rsrc)
      throws AuthException, OrmException, RepositoryNotFoundException, IOException,
          ConfigInvalidException, PermissionBackendException {
    if (self.get() != rsrc.getUser()) {
      permissionBackend.currentUser().check(GlobalPermission.MODIFY_ACCOUNT);
    }
    return apply(rsrc.getUser());
  }

  public List<SshKeyInfo> apply(IdentifiedUser user)
      throws RepositoryNotFoundException, IOException, ConfigInvalidException {
    return Lists.transform(authorizedKeys.getKeys(user.getAccountId()), GetSshKeys::newSshKeyInfo);
  }

  public static SshKeyInfo newSshKeyInfo(AccountSshKey sshKey) {
    SshKeyInfo info = new SshKeyInfo();
    info.seq = sshKey.seq();
    info.sshPublicKey = sshKey.sshPublicKey();
    info.encodedKey = sshKey.encodedKey();
    info.algorithm = sshKey.algorithm();
    info.comment = Strings.emptyToNull(sshKey.comment());
    info.valid = sshKey.valid();
    return info;
  }
}
