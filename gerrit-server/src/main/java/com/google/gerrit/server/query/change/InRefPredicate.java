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

import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.query.change.ChangeQueryBuilder.RepoWalksCache;
import com.google.inject.Provider;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Predicate to list changes included in a ref.
 */
public class InRefPredicate extends RevWalkPredicate {
  private static final Logger log =
      LoggerFactory.getLogger(InRefPredicate.class);

  public InRefPredicate(Provider<ReviewDb> db, RepoWalksCache repoWalksByProject,
      String ref) {
    super(db, repoWalksByProject, ChangeQueryBuilder.FIELD_INREF, ref);
  }

  @Override
  public boolean match(Repository repo, RevWalk rw, Arguments args) {
    try {
      Ref ref = repo.getRef(getValue());
      if (ref != null) {
        return rw.isMergedInto(rw.parseCommit(args.objectId),
            rw.parseCommit(ref.getObjectId()));
      }
    } catch (MissingObjectException e) {
      log.error("one or or more of the commit's parents are not" +
                " available from the object database.", e);
    } catch (IncorrectObjectTypeException e) {
      log.error("one or or more of the commit's parents are not" +
                " actually commit objects.", e);
    } catch (IOException e) {
      log.error("pack file or loose object could not be read.", e);
    }
    return false;
  }

  @Override
  public int getCost() {
    return 1;
  }
}

