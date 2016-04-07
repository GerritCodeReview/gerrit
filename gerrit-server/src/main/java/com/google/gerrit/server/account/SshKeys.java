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

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.reviewdb.client.AccountSshKey;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;

@Singleton
public class SshKeys implements
    ChildCollection<AccountResource, AccountResource.SshKey> {
  private final DynamicMap<RestView<AccountResource.SshKey>> views;
  private final GetSshKeys list;
  private final Provider<CurrentUser> self;
  private final Provider<ReviewDb> dbProvider;
  private final GitRepositoryManager repoManager;
  private final Provider<AllUsersName> allUsersName;
  private final boolean readFromGit;

  @Inject
  SshKeys(DynamicMap<RestView<AccountResource.SshKey>> views,
      GetSshKeys list, Provider<CurrentUser> self,
      Provider<ReviewDb> dbProvider,
      GitRepositoryManager repoManager,
      Provider<AllUsersName> allUsersName,
      @GerritServerConfig Config cfg) {
    this.views = views;
    this.list = list;
    this.self = self;
    this.dbProvider = dbProvider;
    this.repoManager = repoManager;
    this.allUsersName = allUsersName;
    this.readFromGit =
        cfg.getBoolean("user", null, "readSshKeysFromGit", false);
  }

  @Override
  public RestView<AccountResource> list() {
    return list;
  }

  @Override
  public AccountResource.SshKey parse(AccountResource rsrc, IdString id)
      throws ResourceNotFoundException, OrmException, IOException,
      ConfigInvalidException {
    if (self.get() != rsrc.getUser()
        && !self.get().getCapabilities().canModifyAccount()) {
      throw new ResourceNotFoundException();
    }
    return parse(rsrc.getUser(), id);
  }

  public AccountResource.SshKey parse(IdentifiedUser user, IdString id)
      throws ResourceNotFoundException, OrmException, IOException,
      ConfigInvalidException {
    try {
      int seq = Integer.parseInt(id.get(), 10);

      AccountSshKey sshKey;
      if (readFromGit) {
        try (Repository git = repoManager.openRepository(allUsersName.get())) {
          VersionedAuthorizedKeys authorizedKeys =
              new VersionedAuthorizedKeys(user.getAccountId());
          authorizedKeys.load(git);
          sshKey = authorizedKeys.getKey(seq);
        }
      } else {
        sshKey = dbProvider.get().accountSshKeys()
            .get(new AccountSshKey.Id(user.getAccountId(), seq));
      }

      if (sshKey == null) {
        throw new ResourceNotFoundException(id);
      }
      return new AccountResource.SshKey(user, sshKey);
    } catch (NumberFormatException e) {
      throw new ResourceNotFoundException(id);
    }
  }

  @Override
  public DynamicMap<RestView<AccountResource.SshKey>> views() {
    return views;
  }
}
