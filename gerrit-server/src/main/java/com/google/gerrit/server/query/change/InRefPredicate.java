// Copyright (c) 2013, The Linux Foundation. All rights reserved.

package com.google.gerrit.server.query.change;

import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Provider;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.AnyObjectId;
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

  public InRefPredicate(Provider<ReviewDb> db,
      GitRepositoryManager repoManager, String ref) {
    super(db, repoManager, ChangeQueryBuilder.FIELD_INREF, ref);
  }

  @Override
  public boolean match(Repository repo, RevWalk rw, AnyObjectId objectId) {
    try {
      Ref ref = repo.getRef(getValue());
      if (ref != null) {
        return rw.isMergedInto(rw.parseCommit(objectId), rw.parseCommit(ref.getObjectId()));
      }
    } catch (MissingObjectException e) {
      log.error("one or or more of the next commit's parents are not available from the object database.", e);
    } catch (IncorrectObjectTypeException e) {
      log.error("one or or more of the next commit's parents are not actually commit objects.", e);
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

