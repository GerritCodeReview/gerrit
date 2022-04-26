package com.google.gerrit.server.git.meta;

import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.revwalk.RevWalk;

public interface VersionedMetaDataRewriter {

  /**
   * Rewrites the commit history.
   *
   * @param revWalk a {@code RevWalk} instance.
   * @param inserter a {@code ObjectInserter} instance.
   * @param currentTip the {@code ObjectId} of the ref's tip commit.
   * @return the {@code ObjectId} of the ref's new tip commit.
   */
  ObjectId rewriteCommitHistory(RevWalk revWalk, ObjectInserter inserter, ObjectId currentTip)
      throws MissingObjectException, IncorrectObjectTypeException, IOException,
          ConfigInvalidException;
}
