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

import com.google.gerrit.pgm.init.api.InitFlags;
import com.google.gerrit.server.GerritPersonIdentProvider;
import com.google.gerrit.server.account.ExternalId;
import com.google.gerrit.server.account.ExternalIds;
import com.google.gerrit.server.account.ExternalIdsUpdate;
import com.google.gerrit.server.config.SitePaths;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache.FileKey;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.FS;

public class ExternalIdsOnInit {
  private final InitFlags flags;
  private final SitePaths site;
  private final String allUsers;

  @Inject
  public ExternalIdsOnInit(InitFlags flags, SitePaths site, AllUsersNameOnInitProvider allUsers) {
    this.flags = flags;
    this.site = site;
    this.allUsers = allUsers.get();
  }

  public synchronized void insert(String commitMessage, Collection<ExternalId> extIds)
      throws OrmException, IOException {
    File path = getPath();
    if (path != null) {
      try (Repository repo = new FileRepository(path);
          RevWalk rw = new RevWalk(repo);
          ObjectInserter ins = repo.newObjectInserter()) {
        ObjectId rev = ExternalIds.readRevision(repo);

        NoteMap noteMap = ExternalIds.readNoteMap(rw, rev);
        for (ExternalId extId : extIds) {
          ExternalIdsUpdate.insert(ins, noteMap, extId);
        }

        PersonIdent serverIdent = new GerritPersonIdentProvider(flags.cfg).get();
        ExternalIdsUpdate.commit(
            repo, rw, ins, rev, noteMap, commitMessage, serverIdent, serverIdent);
      }
    }
  }

  private File getPath() {
    Path basePath = site.resolve(flags.cfg.getString("gerrit", null, "basePath"));
    if (basePath == null) {
      throw new IllegalStateException("gerrit.basePath must be configured");
    }
    return FileKey.resolve(basePath.resolve(allUsers).toFile(), FS.DETECTED);
  }
}
