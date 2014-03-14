package com.google.gerrit.acceptance.rest.change;

import static com.google.gerrit.acceptance.git.GitUtil.checkout;
import static org.junit.Assert.assertEquals;

import com.google.gerrit.acceptance.git.PushOneCommit;
import com.google.gerrit.reviewdb.client.Project.SubmitType;
import com.google.gwtorm.server.OrmException;

import com.jcraft.jsch.JSchException;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

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

  @Test
  public void submitMultipleChanges()
      throws JSchException, IOException, GitAPIException, OrmException {
    Git git = createProject();
    RevCommit initialHead = getRemoteHead();

    checkout(git, initialHead.getId().getName());
    PushOneCommit.Result change2 = createChange(git, "Change 2", "b", "b");

    checkout(git, initialHead.getId().getName());
    PushOneCommit.Result change3 = createChange(git, "Change 3", "c", "c");

    checkout(git, initialHead.getId().getName());
    PushOneCommit.Result change4 = createChange(git, "Change 4", "d", "d");

    submitStatusOnly(change2.getChangeId());
    submitStatusOnly(change3.getChangeId());
    submit(change4.getChangeId());

    List<RevCommit> log = getRemoteLog();
    RevCommit tip = log.get(0);
    assertEquals(
        change4.getCommit().getShortMessage(),
        tip.getParent(1).getShortMessage());

    tip = tip.getParent(0);
    assertEquals(
        change3.getCommit().getShortMessage(),
        tip.getParent(1).getShortMessage());

    tip = tip.getParent(0);
    assertEquals(
        change2.getCommit().getShortMessage(),
        tip.getShortMessage());

    assertEquals(initialHead.getId(), tip.getParent(0).getId());
  }
}
