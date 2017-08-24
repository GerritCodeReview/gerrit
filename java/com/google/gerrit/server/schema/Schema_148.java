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

package com.google.gerrit.server.schema;

import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import com.google.common.primitives.Ints;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIdReader;
import com.google.gerrit.server.account.externalids.ExternalIdsUpdate;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.sql.SQLException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.notes.Note;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevWalk;

public class Schema_148 extends SchemaVersion {
  private static final String COMMIT_MSG = "Make account IDs of external IDs human-readable";

  private final GitRepositoryManager repoManager;
  private final AllUsersName allUsersName;
  private final PersonIdent serverUser;

  @Inject
  Schema_148(
      Provider<Schema_147> prior,
      GitRepositoryManager repoManager,
      AllUsersName allUsersName,
      @GerritPersonIdent PersonIdent serverUser) {
    super(prior);
    this.repoManager = repoManager;
    this.allUsersName = allUsersName;
    this.serverUser = serverUser;
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException, SQLException {
    try (Repository repo = repoManager.openRepository(allUsersName);
        RevWalk rw = new RevWalk(repo);
        ObjectInserter ins = repo.newObjectInserter()) {
      ObjectId rev = ExternalIdReader.readRevision(repo);
      NoteMap noteMap = ExternalIdReader.readNoteMap(rw, rev);
      boolean dirty = false;
      for (Note note : noteMap) {
        byte[] raw =
            rw.getObjectReader()
                .open(note.getData(), OBJ_BLOB)
                .getCachedBytes(ExternalIdReader.MAX_NOTE_SZ);
        try {
          ExternalId extId = ExternalId.parse(note.getName(), raw, note.getData());

          if (needsUpdate(extId)) {
            ExternalIdsUpdate.upsert(rw, ins, noteMap, extId);
            dirty = true;
          }
        } catch (ConfigInvalidException e) {
          ui.message(
              String.format("Warning: Ignoring invalid external ID note %s", note.getName()));
        }
      }
      if (dirty) {
        ExternalIdsUpdate.commit(repo, rw, ins, rev, noteMap, COMMIT_MSG, serverUser, serverUser);
      }
    } catch (IOException e) {
      throw new OrmException("Failed to update external IDs", e);
    }
  }

  private static boolean needsUpdate(ExternalId extId) {
    Config cfg = new Config();
    cfg.setInt("externalId", extId.key().get(), "accountId", extId.accountId().get());
    return Ints.tryParse(cfg.getString("externalId", extId.key().get(), "accountId")) == null;
  }
}
