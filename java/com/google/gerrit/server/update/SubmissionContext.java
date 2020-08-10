package com.google.gerrit.server.update;

/**
 * Description of a submission passed to BatchRefUpdate so they can report their result.
 *
 * <p>Implementors can subclass this context to add their storage-specific details.
 */
public class SubmissionContext {
  private final String submissionId;
  private final int updatesCount;

  SubmissionContext(String submissionId, int updatesCount) {
    this.updatesCount = updatesCount;
    this.submissionId = submissionId;
  }

  public int getUpdatesCount() {
    return updatesCount;
  }

  public String getSubmissionId() {
    return submissionId;
  }
}
