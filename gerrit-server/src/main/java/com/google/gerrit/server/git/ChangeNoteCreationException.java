package com.google.gerrit.server.git;

import org.eclipse.jgit.revwalk.RevCommit;

public class ChangeNoteCreationException  extends Exception {
  private static final long serialVersionUID = 1L;

  public ChangeNoteCreationException(final String msg) {
    super(msg);
  }

  public ChangeNoteCreationException(final Throwable why) {
    super(why);
  }

  public ChangeNoteCreationException(final RevCommit commit,
      final Throwable cause) {
    super("Couldn't create change note for the following commit: "
        + commit.name(), cause);
  }
}
