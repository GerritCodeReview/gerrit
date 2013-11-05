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

import static com.google.gerrit.acceptance.git.GitUtil.checkout;
import static org.junit.Assert.assertEquals;

import com.google.gerrit.acceptance.git.PushOneCommit;
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
  public void submitWithCherryPick() throws JSchException, IOException,
      GitAPIException {
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
    assertEquals(oldHead, getRemoteHead().getParent(0));
  }

  @Test
  public void submitWithContentMerge() throws JSchException, IOException,
      GitAPIException {
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
    assertEquals(oldHead, getRemoteHead().getParent(0));
  }

  @Test
  public void submitWithContentMerge_Conflict() throws JSchException,
      IOException, GitAPIException {
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
  }

  @Test
  public void submitOutOfOrder() throws JSchException, IOException,
      GitAPIException {
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
    assertEquals(oldHead, getRemoteHead().getParent(0));
  }

  @Test
  public void submitOutOfOrder_Conflict() throws JSchException, IOException,
      GitAPIException {
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
  }

  @Test
  public void submitChangeWithOutdatedDependency() throws JSchException,
      IOException, GitAPIException {
    Git git = createProject();
    PushOneCommit.Result change1 =
        createChange(git, "Change 1", "a.txt", "content");

    PushOneCommit.Result change2 =
        createChange(git, "Change 2", "a.txt", "other content");

    checkout(git, change1.getCommit().getName());
    PushOneCommit.Result change1a =
        createChange(git, "Change 1 (amended)", "a.txt", "content",
            change1.getChangeId());
    submit(change1a.getChangeId());

    RevCommit oldHead = getRemoteHead();
    submit(change2.getChangeId());
    assertEquals(oldHead, getRemoteHead().getParent(0));
  }
}
