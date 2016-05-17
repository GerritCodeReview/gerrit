// Copyright (C) 2016 The Android Open Source Project
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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.gerrit.pgm.init.api.InitFlags;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountSshKey;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.account.AuthorizedKeys;
import com.google.gerrit.server.account.VersionedAuthorizedKeys;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.git.VersionedMetaData;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache.FileKey;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.FS;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class VersionedAuthorizedKeysOnInit extends VersionedMetaData {
  public interface Factory {
    VersionedAuthorizedKeysOnInit create(Account.Id accountId);
  }

  private final Account.Id accountId;
  private final String ref;
  private final String project;
  private final SitePaths site;
  private final InitFlags flags;

  private List<Optional<AccountSshKey>> keys;
  private ObjectId revision;

  @Inject
  public VersionedAuthorizedKeysOnInit(
      AllUsersNameOnInitProvider allUsers,
      SitePaths site,
      InitFlags flags,
      @Assisted Account.Id accountId) {

    this.project = allUsers.get();
    this.site = site;
    this.flags = flags;
    this.accountId = accountId;
    this.ref = RefNames.refsUsers(accountId);
  }

  @Override
  protected String getRefName() {
    return ref;
  }

  public VersionedAuthorizedKeysOnInit load()
      throws IOException, ConfigInvalidException {
    File path = getPath();
    if (path != null) {
      try (Repository repo = new FileRepository(path)) {
        load(repo);
      }
    }
    return this;
  }

  private File getPath() {
    Path basePath = site.resolve(flags.cfg.getString("gerrit", null, "basePath"));
    if (basePath == null) {
      throw new IllegalStateException("gerrit.basePath must be configured");
    }
    return FileKey.resolve(basePath.resolve(project).toFile(), FS.DETECTED);
  }

  @Override
  protected void onLoad() throws IOException, ConfigInvalidException {
    revision = getRevision();
    keys = AuthorizedKeys.parse(accountId, readUTF8(AuthorizedKeys.FILE_NAME));
  }

  public AccountSshKey addKey(String pub) {
    checkState(keys != null, "SSH keys not loaded yet");
    int seq = keys.isEmpty() ? 1 : keys.size() + 1;
    AccountSshKey.Id keyId = new AccountSshKey.Id(accountId, seq);
    AccountSshKey key =
        new VersionedAuthorizedKeys.SimpleSshKeyCreator().create(keyId, pub);
    keys.add(Optional.of(key));
    return key;
  }

  public void save(String message) throws IOException {
    save(new PersonIdent("Gerrit Initialization", "init@gerrit"), message);
  }

  private void save(PersonIdent ident, String msg) throws IOException {
    File path = getPath();
    if (path == null) {
      throw new IOException(project + " does not exist.");
    }

    try (Repository repo = new FileRepository(path);
        ObjectInserter i = repo.newObjectInserter();
        ObjectReader r = repo.newObjectReader();
        RevWalk rw = new RevWalk(reader)) {
      inserter = i;
      reader = r;

      RevTree srcTree = revision != null ? rw.parseTree(revision) : null;
      newTree = readTree(srcTree);

      CommitBuilder commit = new CommitBuilder();
      commit.setAuthor(ident);
      commit.setCommitter(ident);
      commit.setMessage(msg);

      onSave(commit);
      ObjectId res = newTree.writeTree(inserter);
      if (res.equals(srcTree)) {
        return;
      }

      commit.setTreeId(res);
      if (revision != null) {
        commit.addParentId(revision);
      }
      ObjectId newRevision = inserter.insert(commit);
      updateRef(repo, ident, newRevision, "commit: " + msg);
      revision = newRevision;
    } finally {
      inserter = null;
      reader = null;
    }
  }

  @Override
  protected boolean onSave(CommitBuilder commit) throws IOException {
    if (Strings.isNullOrEmpty(commit.getMessage())) {
      commit.setMessage("Updated SSH keys\n");
    }

    saveUTF8(AuthorizedKeys.FILE_NAME, AuthorizedKeys.serialize(keys));
    return true;
  }

  private void updateRef(Repository repo, PersonIdent ident,
      ObjectId newRevision, String refLogMsg) throws IOException {
    RefUpdate ru = repo.updateRef(getRefName());
    ru.setRefLogIdent(ident);
    ru.setNewObjectId(newRevision);
    ru.setExpectedOldObjectId(revision);
    ru.setRefLogMessage(refLogMsg, false);
    RefUpdate.Result r = ru.update();
    switch(r) {
      case FAST_FORWARD:
      case NEW:
      case NO_CHANGE:
        break;
      case FORCED:
      case IO_FAILURE:
      case LOCK_FAILURE:
      case NOT_ATTEMPTED:
      case REJECTED:
      case REJECTED_CURRENT_BRANCH:
      case RENAMED:
      default:
        throw new IOException("Failed to update " + getRefName() + " of "
            + project + ": " + r.name());
    }
  }
}
