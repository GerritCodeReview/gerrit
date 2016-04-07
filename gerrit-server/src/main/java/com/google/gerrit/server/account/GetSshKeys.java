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

import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.gerrit.extensions.common.SshKeyInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Account;
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
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class GetSshKeys implements RestReadView<AccountResource> {

  private final Provider<CurrentUser> self;
  private final Provider<ReviewDb> dbProvider;
  private final GitRepositoryManager repoManager;
  private final Provider<AllUsersName> allUsersName;
  private final boolean readFromGit;

  @Inject
  GetSshKeys(Provider<CurrentUser> self,
      Provider<ReviewDb> dbProvider,
      GitRepositoryManager repoManager,
      Provider<AllUsersName> allUsersName,
      @GerritServerConfig Config cfg) {
    this.self = self;
    this.dbProvider = dbProvider;
    this.repoManager = repoManager;
    this.allUsersName = allUsersName;
    this.readFromGit =
        cfg.getBoolean("user", null, "readSshKeysFromGit", false);
  }

  @Override
  public List<SshKeyInfo> apply(AccountResource rsrc)
      throws AuthException, OrmException, RepositoryNotFoundException,
      IOException, ConfigInvalidException {
    if (self.get() != rsrc.getUser()
        && !self.get().getCapabilities().canModifyAccount()) {
      throw new AuthException("not allowed to get SSH keys");
    }
    return apply(rsrc.getUser());
  }

  public List<SshKeyInfo> apply(IdentifiedUser user) throws OrmException,
      RepositoryNotFoundException, IOException, ConfigInvalidException {
    List<AccountSshKey> keys = readFromGit
        ? readFromGit(user.getAccountId())
        : readFromDb(dbProvider.get(), user.getAccountId());
    return Lists.transform(keys,
        new Function<AccountSshKey, SshKeyInfo>() {
          @Override
          public SshKeyInfo apply(AccountSshKey key) {
            return newSshKeyInfo(key);
          }
        });
  }

  private List<AccountSshKey> readFromGit(Account.Id accountId)
      throws RepositoryNotFoundException, IOException, ConfigInvalidException {
    try (Repository git = repoManager.openRepository(allUsersName.get())) {
      VersionedAuthorizedKeys authorizedKeys =
          new VersionedAuthorizedKeys(accountId);
      authorizedKeys.load(git);
      return authorizedKeys.getKeys();
    }
  }

  public static List<AccountSshKey> readFromDb(ReviewDb db, Account.Id accountId)
      throws OrmException {
    List<AccountSshKey> sshKeys = new ArrayList<>();
    for (AccountSshKey sshKey : db.accountSshKeys().byAccount(accountId)
        .toList()) {
      sshKeys.add(sshKey);
    }
    return sshKeys;
  }

  public static SshKeyInfo newSshKeyInfo(AccountSshKey sshKey) {
    SshKeyInfo info = new SshKeyInfo();
    info.seq = sshKey.getKey().get();
    info.sshPublicKey = sshKey.getSshPublicKey();
    info.encodedKey = sshKey.getEncodedKey();
    info.algorithm = sshKey.getAlgorithm();
    info.comment = Strings.emptyToNull(sshKey.getComment());
    info.valid = sshKey.isValid();
    return info;
  }
}
