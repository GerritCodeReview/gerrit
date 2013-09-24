package com.google.gerrit.acceptance.rest.change;

import static org.junit.Assert.assertEquals;

import com.google.gerrit.acceptance.git.PushOneCommit;
import com.google.gerrit.reviewdb.client.Project.SubmitType;

import com.jcraft.jsch.JSchException;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

import java.io.IOException;

public class SubmitByMergeIfNecessaryIT extends AbstractSubmitByMerge {

  @Override
  protected SubmitType getSubmitType() {
    return SubmitType.MERGE_IF_NECESSARY;
  }

  @Test
  public void submitWithFastForward() throws JSchException, IOException,
      GitAPIException {
    Git git = createProject();
    RevCommit oldHead = getRemoteHead();
    PushOneCommit.Result change = createChange(git);
    submit(change.getChangeId());
    RevCommit head = getRemoteHead();
    assertEquals(change.getCommitId(), head.getId());
    assertEquals(oldHead, head.getParent(0));
  }
}
