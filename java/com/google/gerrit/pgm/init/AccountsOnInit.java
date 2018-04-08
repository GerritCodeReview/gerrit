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
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.gerrit.config.SitePaths;
import com.google.gerrit.pgm.init.api.AllUsersNameOnInitProvider;
import com.google.gerrit.pgm.init.api.InitFlags;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.GerritPersonIdentProvider;
import com.google.gerrit.server.account.AccountProperties;
import com.google.gerrit.server.account.Accounts;
import com.google.gerrit.server.account.InternalAccountUpdate;
import com.google.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEditor.PathEdit;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
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

  public void insert(Account account) throws IOException {
    File path = getPath();
    if (path != null) {
      try (Repository repo = new FileRepository(path);
          ObjectInserter oi = repo.newObjectInserter()) {
        PersonIdent ident =
            new PersonIdent(
                new GerritPersonIdentProvider(flags.cfg).get(), account.getRegisteredOn());

        Config accountConfig = new Config();
        AccountProperties.writeToAccountConfig(
            InternalAccountUpdate.builder()
                .setActive(account.isActive())
                .setFullName(account.getFullName())
                .setPreferredEmail(account.getPreferredEmail())
                .setStatus(account.getStatus())
                .build(),
            accountConfig);

        DirCache newTree = DirCache.newInCore();
        DirCacheEditor editor = newTree.editor();
        final ObjectId blobId =
            oi.insert(Constants.OBJ_BLOB, accountConfig.toText().getBytes(UTF_8));
        editor.add(
            new PathEdit(AccountProperties.ACCOUNT_CONFIG) {
              @Override
              public void apply(DirCacheEntry ent) {
                ent.setFileMode(FileMode.REGULAR_FILE);
                ent.setObjectId(blobId);
              }
            });
        editor.finish();

        ObjectId treeId = newTree.writeTree(oi);

        CommitBuilder cb = new CommitBuilder();
        cb.setTreeId(treeId);
        cb.setCommitter(ident);
        cb.setAuthor(ident);
        cb.setMessage("Create Account");
        ObjectId id = oi.insert(cb);
        oi.flush();

        String refName = RefNames.refsUsers(account.getId());
        RefUpdate ru = repo.updateRef(refName);
        ru.setExpectedOldObjectId(ObjectId.zeroId());
        ru.setNewObjectId(id);
        ru.setRefLogIdent(ident);
        ru.setRefLogMessage("Create Account", false);
        Result result = ru.update();
        if (result != Result.NEW) {
          throw new IOException(
              String.format("Failed to update ref %s: %s", refName, result.name()));
        }
        account.setMetaId(id.name());
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
