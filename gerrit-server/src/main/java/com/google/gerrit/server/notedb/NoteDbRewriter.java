package com.google.gerrit.server.notedb;

import com.google.gwtorm.server.OrmException;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.revwalk.RevWalk;

public interface NoteDbRewriter {
  String getRefName();

  ObjectId rewriteCommitHistory(RevWalk revWalk, ObjectInserter inserter, ObjectId currTip)
      throws IOException, ConfigInvalidException, OrmException;
}
