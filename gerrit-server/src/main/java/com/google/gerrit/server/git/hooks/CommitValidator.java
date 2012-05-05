package com.google.gerrit.server.git.hooks;

import com.google.gerrit.server.project.RefControl;

import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.ReceiveCommand;

import java.io.IOException;

public interface CommitValidator {

  public boolean validCommit(final RefControl ctl, final ReceiveCommand cmd,
      final RevCommit c) throws MissingObjectException, IOException
  ;

}
