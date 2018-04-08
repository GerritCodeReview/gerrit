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

package com.google.gerrit.pgm.init.api;

import com.google.gerrit.config.GerritPersonIdentProvider;
import com.google.gerrit.config.SitePaths;
import com.google.gerrit.server.git.meta.VersionedMetaData;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
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

public abstract class VersionedMetaDataOnInit extends VersionedMetaData {

  protected final String project;
  private final InitFlags flags;
  private final SitePaths site;
  private final String ref;

  protected VersionedMetaDataOnInit(InitFlags flags, SitePaths site, String project, String ref) {
    this.flags = flags;
    this.site = site;
    this.project = project;
    this.ref = ref;
  }

  @Override
  protected String getRefName() {
    return ref;
  }

  public VersionedMetaDataOnInit load() throws IOException, ConfigInvalidException {
    File path = getPath();
    if (path != null) {
      try (Repository repo = new FileRepository(path)) {
        load(repo);
      }
    }
    return this;
  }

  public void save(String message) throws IOException, ConfigInvalidException {
    save(new GerritPersonIdentProvider(flags.cfg).get(), message);
  }

  protected void save(PersonIdent ident, String msg) throws IOException, ConfigInvalidException {
    File path = getPath();
    if (path == null) {
      throw new IOException(project + " does not exist.");
    }

    try (Repository repo = new FileRepository(path);
        ObjectInserter i = repo.newObjectInserter();
        ObjectReader r = repo.newObjectReader();
        RevWalk rw = new RevWalk(r)) {
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
      revision = rw.parseCommit(newRevision);
    } finally {
      inserter = null;
      reader = null;
    }
  }

  private void updateRef(Repository repo, PersonIdent ident, ObjectId newRevision, String refLogMsg)
      throws IOException {
    RefUpdate ru = repo.updateRef(getRefName());
    ru.setRefLogIdent(ident);
    ru.setNewObjectId(newRevision);
    ru.setExpectedOldObjectId(revision);
    ru.setRefLogMessage(refLogMsg, false);
    RefUpdate.Result r = ru.update();
    switch (r) {
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
      case REJECTED_MISSING_OBJECT:
      case REJECTED_OTHER_REASON:
      default:
        throw new IOException(
            "Failed to update " + getRefName() + " of " + project + ": " + r.name());
    }
  }

  private File getPath() {
    Path basePath = site.resolve(flags.cfg.getString("gerrit", null, "basePath"));
    if (basePath == null) {
      throw new IllegalStateException("gerrit.basePath must be configured");
    }
    return FileKey.resolve(basePath.resolve(project).toFile(), FS.DETECTED);
  }
}
