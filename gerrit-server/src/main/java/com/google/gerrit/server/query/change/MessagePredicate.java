// Copyright (C) 2010 The Android Open Source Project
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

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.MessageRevFilter;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Predicate to match changes that contains specified text in commit messages
 * body.
 */
public class MessagePredicate extends OperatorPredicate<ChangeData, PatchSet> {

  private static final Logger log =
      LoggerFactory.getLogger(MessagePredicate.class);

  private final Provider<ReviewDb> db;
  private final GitRepositoryManager repoManager;
  private final RevFilter rFilter;

  public MessagePredicate(Provider<ReviewDb> db,
      GitRepositoryManager repoManager, String text) {
    super(ChangeQueryBuilder.FIELD_MESSAGE, text);
    this.db = db;
    this.repoManager = repoManager;
    this.rFilter = MessageRevFilter.create(text);
  }

  @Override
  public boolean match(final ChangeData object, final PatchSet subobject)
      throws OrmException {
    final PatchSet patchSet = object.currentPatchSet(db);

    if (patchSet == null) {
      return false;
    }

    final RevId revision = patchSet.getRevision();

    if (revision == null) {
      return false;
    }

    final AnyObjectId objectId = ObjectId.fromString(revision.get());

    if (objectId == null) {
      return false;
    }

    final Change change = object.change(db);

    if (change == null) {
      return false;
    }

    final Project.NameKey projectName = change.getProject();

    if (projectName == null) {
      return false;
    }

    try {
      final Repository repo = repoManager.openRepository(projectName);
      try {
        final RevWalk rw = new RevWalk(repo);
        try {
          return rFilter.include(rw, rw.parseCommit(objectId));
        } finally {
          rw.release();
        }
      } finally {
        repo.close();
      }
    } catch (RepositoryNotFoundException e) {
      log.error("Repository \"" + projectName.get() + "\" unknown.", e);
    } catch (MissingObjectException e) {
      log.error(projectName.get() + "\" commit does not exist.", e);
    } catch (IncorrectObjectTypeException e) {
      log.error(projectName.get() + "\" revision is not a commit.", e);
    } catch (IOException e) {
      log.error("Could not search for commit message in \"" + projectName.get()
          + "\" repository.", e);
    }

    return false;
  }

  @Override
  public int getCost() {
    return 1;
  }
}
