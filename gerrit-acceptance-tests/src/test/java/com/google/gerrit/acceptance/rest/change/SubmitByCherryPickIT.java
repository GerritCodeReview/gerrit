// Copyright (C) 2013 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.acceptance.rest.change;

import static com.google.gerrit.acceptance.GitUtil.checkout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeStatus;
import com.google.gerrit.extensions.common.ListChangesOption;
import com.google.gerrit.reviewdb.client.Project.SubmitType;
import com.google.gwtorm.server.OrmException;

import com.jcraft.jsch.JSchException;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

public class SubmitByCherryPickIT extends AbstractSubmit {

  @Override
  protected SubmitType getSubmitType() {
    return SubmitType.CHERRY_PICK;
  }

  @Test
  public void submitWithCherryPickIfFastForwardPossible() throws JSchException,
      IOException, GitAPIException {
    Git git = createProject();
    PushOneCommit.Result change = createChange(git);
    submit(change.getChangeId());
    assertCherryPick(git, false);
    assertEquals(change.getCommit().getParent(0),
        getRemoteHead().getParent(0));
  }

  @Test
  public void submitWithCherryPick() throws Exception {
    Git git = createProject();
    RevCommit initialHead = getRemoteHead();
    PushOneCommit.Result change =
        createChange(git, "Change 1", "a.txt", "content");
    submit(change.getChangeId());

    RevCommit oldHead = getRemoteHead();
    checkout(git, initialHead.getId().getName());
    PushOneCommit.Result change2 =
        createChange(git, "Change 2", "b.txt", "other content");
    submit(change2.getChangeId());
    assertCherryPick(git, false);
    RevCommit newHead = getRemoteHead();
    assertEquals(1, newHead.getParentCount());
    assertEquals(oldHead, newHead.getParent(0));
    assertCurrentRevision(change2.getChangeId(), 2, newHead);
    assertSubmitter(change2.getChangeId(), 1);
    assertSubmitter(change2.getChangeId(), 2);
  }

  @Test
  public void submitWithContentMerge() throws Exception {
    Git git = createProject();
    setUseContentMerge();
    PushOneCommit.Result change =
        createChange(git, "Change 1", "a.txt", "aaa\nbbb\nccc\n");
    submit(change.getChangeId());
    PushOneCommit.Result change2 =
        createChange(git, "Change 2", "a.txt", "aaa\nbbb\nccc\nddd\n");
    submit(change2.getChangeId());

    RevCommit oldHead = getRemoteHead();
    checkout(git, change.getCommitId().getName());
    PushOneCommit.Result change3 =
        createChange(git, "Change 3", "a.txt", "bbb\nccc\n");
    submit(change3.getChangeId());
    assertCherryPick(git, true);
    RevCommit newHead = getRemoteHead();
    assertEquals(oldHead, newHead.getParent(0));
    assertApproved(change3.getChangeId());
    assertCurrentRevision(change3.getChangeId(), 2, newHead);
    assertSubmitter(change2.getChangeId(), 1);
    assertSubmitter(change2.getChangeId(), 2);
  }

  @Test
  public void submitWithContentMerge_Conflict() throws Exception {
    Git git = createProject();
    setUseContentMerge();
    RevCommit initialHead = getRemoteHead();
    PushOneCommit.Result change =
        createChange(git, "Change 1", "a.txt", "content");
    submit(change.getChangeId());

    RevCommit oldHead = getRemoteHead();
    checkout(git, initialHead.getId().getName());
    PushOneCommit.Result change2 =
        createChange(git, "Change 2", "a.txt", "other content");
    submitWithConflict(change2.getChangeId());
    assertEquals(oldHead, getRemoteHead());
    assertCurrentRevision(change2.getChangeId(), 1, change2.getCommitId());
    assertSubmitter(change2.getChangeId(), 1);
  }

  @Test
  public void submitOutOfOrder() throws Exception {
    Git git = createProject();
    RevCommit initialHead = getRemoteHead();
    PushOneCommit.Result change =
        createChange(git, "Change 1", "a.txt", "content");
    submit(change.getChangeId());

    RevCommit oldHead = getRemoteHead();
    checkout(git, initialHead.getId().getName());
    createChange(git, "Change 2", "b.txt", "other content");
    PushOneCommit.Result change3 =
        createChange(git, "Change 3", "c.txt", "different content");
    submit(change3.getChangeId());
    assertCherryPick(git, false);
    RevCommit newHead = getRemoteHead();
    assertEquals(oldHead, newHead.getParent(0));
    assertApproved(change3.getChangeId());
    assertCurrentRevision(change3.getChangeId(), 2, newHead);
    assertSubmitter(change3.getChangeId(), 1);
    assertSubmitter(change3.getChangeId(), 2);
  }

  @Test
  public void submitOutOfOrder_Conflict() throws Exception {
    Git git = createProject();
    RevCommit initialHead = getRemoteHead();
    PushOneCommit.Result change =
        createChange(git, "Change 1", "a.txt", "content");
    submit(change.getChangeId());

    RevCommit oldHead = getRemoteHead();
    checkout(git, initialHead.getId().getName());
    createChange(git, "Change 2", "b.txt", "other content");
    PushOneCommit.Result change3 =
        createChange(git, "Change 3", "b.txt", "different content");
    submitWithConflict(change3.getChangeId());
    assertEquals(oldHead, getRemoteHead());
    assertCurrentRevision(change3.getChangeId(), 1, change3.getCommitId());
    assertSubmitter(change3.getChangeId(), 1);
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
    assertEquals(
        change4.getCommit().getShortMessage(),
        log.get(0).getShortMessage());
    assertSame(log.get(1), log.get(0).getParent(0));

    assertEquals(
        change3.getCommit().getShortMessage(),
        log.get(1).getShortMessage());
    assertSame(log.get(2), log.get(1).getParent(0));

    assertEquals(
        change2.getCommit().getShortMessage(),
        log.get(2).getShortMessage());
    assertSame(log.get(3), log.get(2).getParent(0));

    assertEquals(initialHead.getId(), log.get(3).getId());
  }

  @Test
  public void submitDependentNonConflictingChangesOutOfOrder() throws Exception {
    Git git = createProject();
    RevCommit initialHead = getRemoteHead();

    checkout(git, initialHead.getId().getName());
    PushOneCommit.Result change2 = createChange(git, "Change 2", "b", "b");
    PushOneCommit.Result change3 = createChange(git, "Change 3", "c", "c");
    assertEquals(change3.getCommit().getParent(0), change2.getCommit());

    // Submit succeeds; change3 is successfully cherry-picked onto head.
    submit(change3.getChangeId());
    // Submit succeeds; change2 is successfully cherry-picked onto head
    // (which was change3's cherry-pick).
    submit(change2.getChangeId());

    // change2 is the new tip.
    List<RevCommit> log = getRemoteLog();
    assertEquals(log.get(0).getShortMessage(),
        change2.getCommit().getShortMessage());
    assertEquals(log.get(0).getParent(0), log.get(1));

    assertEquals(log.get(1).getShortMessage(),
        change3.getCommit().getShortMessage());
    assertEquals(log.get(1).getParent(0), log.get(2));

    assertEquals(log.get(2).getId(), initialHead.getId());
  }

  @Test
  public void submitDependentConflictingChangesOutOfOrder() throws Exception {
    Git git = createProject();
    RevCommit initialHead = getRemoteHead();

    checkout(git, initialHead.getId().getName());
    PushOneCommit.Result change2 = createChange(git, "Change 2", "b", "b1");
    PushOneCommit.Result change3 = createChange(git, "Change 3", "b", "b2");
    assertEquals(change3.getCommit().getParent(0), change2.getCommit());

    // Submit fails; change3 contains the delta "b1" -> "b2", which cannot be
    // applied against tip.
    submitWithConflict(change3.getChangeId());

    ChangeInfo info3 = get(change3.getChangeId(), ListChangesOption.MESSAGES);
    assertEquals(info3.status, ChangeStatus.NEW);
    String msg = Iterables.getLast(info3.messages).message;
    assertTrue(msg.toLowerCase().contains("path conflict"));

    // Tip has not changed.
    List<RevCommit> log = getRemoteLog();
    assertEquals(log.get(0), initialHead.getId());
  }
}
