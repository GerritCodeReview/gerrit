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

package com.google.gerrit.pgm.init;

import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.git.VersionedMetaData;
import com.google.inject.Inject;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache.FileKey;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.FS;

import java.io.File;
import java.io.IOException;

public class AllProjectsConfig extends VersionedMetaData {
  private final String project;
  private final SitePaths site;
  private final InitFlags flags;

  private Config cfg;
  private ObjectId revision;

  @Inject
  AllProjectsConfig(AllProjectsNameOnInitProvider allProjects, SitePaths site,
      InitFlags flags) {
    this.project = allProjects.get();
    this.site = site;
    this.flags = flags;

  }

  @Override
  protected String getRefName() {
    return RefNames.REFS_CONFIG;
  }

  private File getPath() {
    File basePath = site.resolve(flags.cfg.getString("gerrit", null, "basePath"));
    if (basePath == null) {
      throw new IllegalStateException("gerrit.basePath must be configured");
    }
    return FileKey.resolve(new File(basePath, project), FS.DETECTED);
  }

  public Config load() throws IOException, ConfigInvalidException {
    File path = getPath();
    if (path == null) {
      return null;
    }

    Repository repo = new FileRepository(path);
    try {
      load(repo);
    } finally {
      repo.close();
    }
    return cfg;
  }

  @Override
  protected void onLoad() throws IOException, ConfigInvalidException {
    cfg = readConfig(ProjectConfig.PROJECT_CONFIG);
    revision = getRevision();
  }

  @Override
  protected boolean onSave(CommitBuilder commit) throws IOException,
      ConfigInvalidException {
    throw new UnsupportedOperationException();
  }

  void save(String message) throws IOException {
    save(new PersonIdent("Gerrit Initialization", "init@gerrit"), message);
  }

  public void save(String pluginName, String message) throws IOException {
    save(new PersonIdent(pluginName, pluginName + "@gerrit"),
        "Update from plugin " + pluginName + ": " + message);
  }

  private void save(PersonIdent ident, String msg) throws IOException {
    File path = getPath();
    if (path == null) {
      throw new IOException("All-Projects does not exist.");
    }

    Repository repo = new FileRepository(path);
    try {
      inserter = repo.newObjectInserter();
      reader = repo.newObjectReader();
      try {
        RevWalk rw = new RevWalk(reader);
        try {
          RevTree srcTree = revision != null ? rw.parseTree(revision) : null;
          newTree = readTree(srcTree);
          saveConfig(ProjectConfig.PROJECT_CONFIG, cfg);
          ObjectId res = newTree.writeTree(inserter);
          if (res.equals(srcTree)) {
            // If there are no changes to the content, don't create the commit.
            return;
          }

          CommitBuilder commit = new CommitBuilder();
          commit.setAuthor(ident);
          commit.setCommitter(ident);
          commit.setMessage(msg);
          commit.setTreeId(res);
          if (revision != null) {
            commit.addParentId(revision);
          }
          ObjectId newRevision = inserter.insert(commit);
          updateRef(repo, ident, newRevision, "commit: " + msg);
          revision = newRevision;
        } finally {
          rw.close();
        }
      } finally {
        if (inserter != null) {
          inserter.close();
          inserter = null;
        }
        if (reader != null) {
          reader.close();
          reader = null;
        }
      }
    } finally {
      repo.close();
    }
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
      default:
        throw new IOException("Failed to update " + getRefName() + " of "
            + project + ": " + r.name());
    }
  }
}
