// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.edit;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;

/* Revsion edit reader command */
@Singleton
public class RevisionEditReader {

  private final GitRepositoryManager gitManager;
  private final Provider<CurrentUser> currentUser;
  private final Provider<ReviewDb> db;

  @Inject
  public RevisionEditReader(GitRepositoryManager gitManager,
      Provider<CurrentUser> currentUser,
      Provider<ReviewDb> db) {
    this.gitManager = gitManager;
    this.currentUser = currentUser;
    this.db = db;
  }

  public PatchSet read(Change change)
      throws AuthException, IOException {
    if (!currentUser.get().isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }
    Repository repo = gitManager.openRepository(change.getProject());
    RevWalk rw = null;
    try {
      IdentifiedUser me = (IdentifiedUser)currentUser.get();
      Ref ref = RevisionEdit.getChangeEditRef(repo, me, change.getId());
      if (ref == null) {
        return null;
      }
      RevisionEdit edit = new RevisionEdit(me, change.getId(), ref);
      rw = new RevWalk(repo);
      RevCommit commit = edit.getCommit(repo, rw);
      Preconditions.checkState(commit.getParentCount() == 1);
      RevCommit parentCommit = commit.getParent(0);
      RevId parentRev = new RevId(ObjectId.toString(parentCommit.getId()));
      PatchSet parentPatchSet;
      try {
        parentPatchSet = Iterables.getOnlyElement(
            db.get().patchSets().byRevision(parentRev).toList());
      } catch (OrmException e) {
        throw new IOException(e);
      }
      return edit.getPatchSet(repo, parentPatchSet.getId().get());
    } finally {
      if (rw != null) {
        rw.release();
      }
      repo.close();
    }
  }
}
