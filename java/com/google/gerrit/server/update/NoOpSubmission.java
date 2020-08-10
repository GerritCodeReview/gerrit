package com.google.gerrit.server.update;

import java.util.Map;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ReceiveCommand;

public class NoOpSubmission implements Submission {

  @Override
  public SubmissionContext getSubmissionContext() {
    return null;
  }

  @Override
  public void completed() {
    // noop
  }

  public static class Builder implements Submission.Builder {

    @Override
    public void addEntry(Repository repository, Map<String, ReceiveCommand> refUpdates) {}

    @Override
    public Submission done() {
      return new NoOpSubmission();
    }
  }
}
