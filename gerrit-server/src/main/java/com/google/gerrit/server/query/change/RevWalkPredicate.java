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

package com.google.gerrit.server.query.change;

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.query.OperatorPredicate;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Provider;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public abstract class RevWalkPredicate extends OperatorPredicate<ChangeData> {

  private static final Logger log =
      LoggerFactory.getLogger(RevWalkPredicate.class);
  public final Provider<ReviewDb> db;
  public final GitRepositoryManager repoManager;

  public RevWalkPredicate(Provider<ReviewDb> db,
      GitRepositoryManager repoManager, String operator, String ref) {
    super(operator, ref);
    this.db = db;
    this.repoManager = repoManager;
  }

  @Override
  public boolean match(ChangeData object) throws OrmException {
    final PatchSet patchSet = object.currentPatchSet(db);
    if (patchSet == null) {
      throw new OrmException("Unable to read patchset for " + object.getId());
    }

    final RevId revision = patchSet.getRevision();
    if (revision == null) {
      throw new OrmException("Unable to read revision for " + object.getId());
    }

    final AnyObjectId objectId = ObjectId.fromString(revision.get());
    if (objectId == null) {
      throw new OrmException("Unable to read objectId(SHA-1) for " + object.getId());
    }

    Change change = object.change(db);
    if (change == null) {
      throw new OrmException("Unable to get change for " + object.getId());
    }

    final Project.NameKey projectName = change.getProject();
    if (projectName == null) {
      throw new OrmException("Unable to get projectName for " + object.getId());
    }

    try {
      final Repository repo = repoManager.openRepository(projectName);
      try {
        final RevWalk rw = new RevWalk(repo);
        try {
          return match(repo, rw, objectId);
        } finally {
          rw.release();
        }
      } finally {
        repo.close();
      }
    } catch (RepositoryNotFoundException e) {
      log.error(projectName.get() + " does not denote an existing repository", e);
    } catch (IOException e) {
      log.error(projectName.get() + " cannot be read as a repository", e);
    }
    return false;
  }

  public abstract boolean match(Repository repo, RevWalk rw, AnyObjectId objectId);

}
