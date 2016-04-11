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

import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.DeleteSshKey.Input;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.ssh.SshKeyCache;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;

@Singleton
public class DeleteSshKey implements
    RestModifyView<AccountResource.SshKey, Input> {
  public static class Input {
  }

  private final Provider<CurrentUser> self;
  private final GitRepositoryManager repoManager;
  private final Provider<AllUsersName> allUsersName;
  private final Provider<MetaDataUpdate.User> metaDataUpdateFactory;
  private final SshKeyCache sshKeyCache;

  @Inject
  DeleteSshKey(Provider<CurrentUser> self,
      GitRepositoryManager repoManager,
      Provider<AllUsersName> allUsersName,
      Provider<MetaDataUpdate.User> metaDataUpdateFactory,
      SshKeyCache sshKeyCache) {
    this.self = self;
    this.repoManager = repoManager;
    this.allUsersName = allUsersName;
    this.metaDataUpdateFactory = metaDataUpdateFactory;
    this.sshKeyCache = sshKeyCache;
  }

  @Override
  public Response<?> apply(AccountResource.SshKey rsrc, Input input)
      throws AuthException, OrmException, RepositoryNotFoundException,
      IOException, ConfigInvalidException {
    if (self.get() != rsrc.getUser()
        && !self.get().getCapabilities().canAdministrateServer()) {
      throw new AuthException("not allowed to delete SSH keys");
    }

    try (MetaDataUpdate md =
            metaDataUpdateFactory.get().create(allUsersName.get());
        Repository git = repoManager.openRepository(allUsersName.get())) {
      VersionedAuthorizedKeys authorizedKeys =
          new VersionedAuthorizedKeys(rsrc.getUser().getAccountId());
      authorizedKeys.load(md);
      if (authorizedKeys.deleteKey(rsrc.getSshKey().getKey().get())) {
        authorizedKeys.commit(md);
        sshKeyCache.evict(rsrc.getUser().getUserName());
      }
    }

    return Response.none();
  }
}
