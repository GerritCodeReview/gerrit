package com.google.gerrit.acceptance.rest.change;

import static org.junit.Assert.assertEquals;

import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.reviewdb.client.Project.SubmitType;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

public class SubmitByMergeIfNecessaryIT extends AbstractSubmitByMerge {

  @Override
  protected SubmitType getSubmitType() {
    return SubmitType.MERGE_IF_NECESSARY;
  }

  @Test
  public void submitWithFastForward() throws Exception {
    Git git = createProject();
    RevCommit oldHead = getRemoteHead();
    PushOneCommit.Result change = createChange(git);
    submit(change.getChangeId());
    RevCommit head = getRemoteHead();
    assertEquals(change.getCommitId(), head.getId());
    assertEquals(oldHead, head.getParent(0));
    assertSubmitter(change.getChangeId(), 1);
  }
}
