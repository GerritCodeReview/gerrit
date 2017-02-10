package com.google.gerrit.server.notedb;

import com.google.gwtorm.server.OrmException;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.revwalk.RevWalk;

public interface NoteDbRewriter {
  /** Get the name of the target ref to be rewrote. */
  String getRefName();

  /**
   * Rewrite the commit history.
   *
   * @param revWalk a {@code RevWalk} instance.
   * @param inserter a {@code ObjectInserter} instance.
   * @param currTip the {@code ObjectId} of the ref's tip commit.
   * @return the {@code ObjectId} of the ref's new tip commit.
   * @throws IOException
   * @throws ConfigInvalidException
   * @throws OrmException
   */
  ObjectId rewriteCommitHistory(RevWalk revWalk, ObjectInserter inserter, ObjectId currTip)
      throws IOException, ConfigInvalidException, OrmException;
}
