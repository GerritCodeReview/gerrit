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

import com.google.gerrit.config.GerritPersonIdentProvider;
import com.google.gerrit.config.SitePaths;
import com.google.gerrit.pgm.init.api.AllUsersNameOnInitProvider;
import com.google.gerrit.pgm.init.api.InitFlags;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIdNotes;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache.FileKey;
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
      throws OrmException, IOException, ConfigInvalidException {
    File path = getPath();
    if (path != null) {
      try (Repository allUsersRepo = new FileRepository(path)) {
        ExternalIdNotes extIdNotes = ExternalIdNotes.loadNoCacheUpdate(allUsersRepo);
        extIdNotes.insert(extIds);
        try (MetaDataUpdate metaDataUpdate =
            new MetaDataUpdate(
                GitReferenceUpdated.DISABLED, new Project.NameKey(allUsers), allUsersRepo)) {
          PersonIdent serverIdent = new GerritPersonIdentProvider(flags.cfg).get();
          metaDataUpdate.getCommitBuilder().setAuthor(serverIdent);
          metaDataUpdate.getCommitBuilder().setCommitter(serverIdent);
          metaDataUpdate.getCommitBuilder().setMessage(commitMessage);
          extIdNotes.commit(metaDataUpdate);
        }
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
