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

import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Provider;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.AnyObjectId;
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
public class MessagePredicate extends RevWalkPredicate {

  private static final Logger log =
      LoggerFactory.getLogger(MessagePredicate.class);

  private final RevFilter rFilter;

  public MessagePredicate(Provider<ReviewDb> db,
      GitRepositoryManager repoManager, String text) {
    super(db, repoManager, ChangeQueryBuilder.FIELD_MESSAGE, text);
    this.rFilter = MessageRevFilter.create(text);
  }

  @Override
  public boolean match(Repository repo, RevWalk rw, AnyObjectId objectId) {
    try {
      return rFilter.include(rw, rw.parseCommit(objectId));
    } catch (MissingObjectException e) {
      log.error(objectId.getName() + " commit does not exist.", e);
    } catch (IncorrectObjectTypeException e) {
      log.error(objectId.getName() + " revision is not a commit.", e);
    } catch (IOException e) {
      log.error("Could not search for commit message in " +
                   objectId.getName(),e);
    }
    return false;
  }

  @Override
  public int getCost() {
    return 1;
  }
}
