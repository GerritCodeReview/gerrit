// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.pgm.init;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.pgm.init.api.InitFlags;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.GerritPersonIdentProvider;
import com.google.gerrit.server.account.Accounts;
import com.google.gerrit.server.account.AccountsUpdate;
import com.google.gerrit.server.config.SitePaths;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache.FileKey;
import org.eclipse.jgit.util.FS;

public class AccountsOnInit {
  private final InitFlags flags;
  private final SitePaths site;
  private final String allUsers;

  @Inject
  public AccountsOnInit(InitFlags flags, SitePaths site, AllUsersNameOnInitProvider allUsers) {
    this.flags = flags;
    this.site = site;
    this.allUsers = allUsers.get();
  }

  public void insert(ReviewDb db, Account account) throws OrmException, IOException {
    db.accounts().insert(ImmutableSet.of(account));

    File path = getPath();
    if (path != null) {
      try (Repository repo = new FileRepository(path);
          ObjectInserter oi = repo.newObjectInserter()) {
        PersonIdent serverIdent = new GerritPersonIdentProvider(flags.cfg).get();
        AccountsUpdate.createUserBranch(
            repo, oi, serverIdent, serverIdent, account.getId(), account.getRegisteredOn());
      }
    }
  }

  public boolean hasAnyAccount() throws IOException {
    File path = getPath();
    if (path == null) {
      return false;
    }

    try (Repository repo = new FileRepository(path)) {
      return Accounts.hasAnyAccount(repo);
    }
  }

  private File getPath() {
    Path basePath = site.resolve(flags.cfg.getString("gerrit", null, "basePath"));
    checkArgument(basePath != null, "gerrit.basePath must be configured");
    return FileKey.resolve(basePath.resolve(allUsers).toFile(), FS.DETECTED);
  }
}
