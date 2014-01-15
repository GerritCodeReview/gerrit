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

import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.reviewdb.client.Project.SubmitType;

import com.jcraft.jsch.JSchException;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

import java.io.IOException;

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
}
