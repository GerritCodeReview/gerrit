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

package com.google.gerrit.server.account;

import static com.google.gerrit.server.account.ExternalId.toAccountExternalIds;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * This class allows to do batch updates to external IDs.
 *
 * <p>For NoteDb all updates will result in a single commit to the refs/meta/external-ids branch.
 * This means callers can prepare many updates by invoking {@link #replace(ExternalId, ExternalId)}
 * multiple times and when {@link ExternalIdsBatchUpdate#commit(ReviewDb, String)} is invoked a
 * single NoteDb commit is created that contains all the prepared updates.
 */
public class ExternalIdsBatchUpdate {
  private final GitRepositoryManager repoManager;
  private final AllUsersName allUsersName;
  private final PersonIdent serverIdent;
  private final Set<ExternalId> toAdd = new HashSet<>();
  private final Set<ExternalId> toDelete = new HashSet<>();

  @Inject
  public ExternalIdsBatchUpdate(
      GitRepositoryManager repoManager,
      AllUsersName allUsersName,
      @GerritPersonIdent PersonIdent serverIdent) {
    this.repoManager = repoManager;
    this.allUsersName = allUsersName;
    this.serverIdent = serverIdent;
  }

  /**
   * Adds an external ID replacement to the batch.
   *
   * <p>The actual replacement is only done when {@link #commit(ReviewDb, String)} is invoked.
   */
  public void replace(ExternalId extIdToDelete, ExternalId extIdToAdd) {
    ExternalIdsUpdate.checkSameAccount(ImmutableSet.of(extIdToDelete, extIdToAdd));
    toAdd.add(extIdToAdd);
    toDelete.add(extIdToDelete);
  }

  /**
   * Commits this batch.
   *
   * <p>This means external ID replacements which were prepared by invoking {@link
   * #replace(ExternalId, ExternalId)} are now executed. Deletion of external IDs is done before
   * adding the new external IDs. This means if an external ID is specified for deletion and an
   * external ID with the same key is specified to be added, the old external ID with that key is
   * deleted first and then the new external ID is added (so the external ID for that key is
   * replaced).
   *
   * <p>For NoteDb a single commit is created that contains all the external ID updates.
   */
  public void commit(ReviewDb db, String commitMessage)
      throws IOException, OrmException, ConfigInvalidException {
    if (toDelete.isEmpty() && toAdd.isEmpty()) {
      return;
    }

    db.accountExternalIds().delete(toAccountExternalIds(toDelete));
    db.accountExternalIds().insert(toAccountExternalIds(toAdd));

    try (Repository repo = repoManager.openRepository(allUsersName);
        RevWalk rw = new RevWalk(repo);
        ObjectInserter ins = repo.newObjectInserter()) {
      ObjectId rev = ExternalIdsUpdate.readRevision(repo);

      NoteMap noteMap = ExternalIdsUpdate.readNoteMap(rw, rev);

      for (ExternalId extId : toDelete) {
        ExternalIdsUpdate.remove(rw, noteMap, extId);
      }

      for (ExternalId extId : toAdd) {
        ExternalIdsUpdate.insert(ins, noteMap, extId);
      }

      ExternalIdsUpdate.commit(
          repo, rw, ins, rev, noteMap, commitMessage, serverIdent, serverIdent);
    }

    toAdd.clear();
    toDelete.clear();
  }
}
