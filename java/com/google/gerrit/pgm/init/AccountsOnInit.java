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

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.pgm.init.api.AllUsersNameOnInitProvider;
import com.google.gerrit.pgm.init.api.InitFlags;
import com.google.gerrit.server.GerritPersonIdentProvider;
import com.google.gerrit.server.account.AccountDelta;
import com.google.gerrit.server.account.AccountProperties;
import com.google.gerrit.server.account.Accounts;
import com.google.gerrit.server.config.GitBasePathProvider;
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
  private final Path basePath;
  private final String allUsers;

  @Inject
  public AccountsOnInit(
      InitFlags flags, GitBasePathProvider basePathProvider, AllUsersNameOnInitProvider allUsers) {
    this.flags = flags;
    this.basePath = basePathProvider.get();
    this.allUsers = allUsers.get();
  }

  public Account insert(Account.Builder account) throws IOException {
    File path = getPath();
    try (Repository repo = new FileRepository(path);
        ObjectInserter oi = repo.newObjectInserter()) {
      PersonIdent ident =
          new PersonIdent(new GerritPersonIdentProvider(flags.cfg).get(), account.registeredOn());

      Config accountConfig = new Config();
      AccountProperties.writeToAccountConfig(
          AccountDelta.builder()
              .setActive(!account.inactive())
              .setFullName(account.fullName())
              .setPreferredEmail(account.preferredEmail())
              .setStatus(account.status())
              .build(),
          accountConfig);

      DirCache newTree = DirCache.newInCore();
      DirCacheEditor editor = newTree.editor();
      final ObjectId blobId = oi.insert(Constants.OBJ_BLOB, accountConfig.toText().getBytes(UTF_8));
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

      String refName = RefNames.refsUsers(account.id());
      RefUpdate ru = repo.updateRef(refName);
      ru.setExpectedOldObjectId(ObjectId.zeroId());
      ru.setNewObjectId(id);
      ru.setRefLogIdent(ident);
      ru.setRefLogMessage("Create Account", false);
      Result result = ru.update();
      if (result != Result.NEW) {
        throw new IOException(String.format("Failed to update ref %s: %s", refName, result.name()));
      }
      account.setMetaId(id.name());
    }
    return account.build();
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
    File file = basePath.resolve(allUsers).toFile();
    File resolvedFile = FileKey.resolve(file, FS.DETECTED);
    requireNonNull(resolvedFile, () -> String.format("%s does not exist", file.getAbsolutePath()));
    return resolvedFile;
  }
}
