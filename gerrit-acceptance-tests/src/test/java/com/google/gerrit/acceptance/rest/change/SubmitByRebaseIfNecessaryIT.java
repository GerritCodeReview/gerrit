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
import com.google.gerrit.extensions.common.SubmitType;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

public class SubmitByRebaseIfNecessaryIT extends AbstractSubmit {

  @Override
  protected SubmitType getSubmitType() {
    return SubmitType.REBASE_IF_NECESSARY;
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
    assertApproved(change.getChangeId());
    assertCurrentRevision(change.getChangeId(), 1, head);
    assertSubmitter(change.getChangeId(), 1);
  }

  @Test
  public void submitWithRebase() throws Exception {
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
    assertRebase(git, false);
    RevCommit head = getRemoteHead();
    assertEquals(oldHead, head.getParent(0));
    assertApproved(change2.getChangeId());
    assertCurrentRevision(change2.getChangeId(), 2, head);
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
    assertRebase(git, true);
    RevCommit head = getRemoteHead();
    assertEquals(oldHead, head.getParent(0));
    assertApproved(change3.getChangeId());
    assertCurrentRevision(change3.getChangeId(), 2, head);
    assertSubmitter(change3.getChangeId(), 1);
    assertSubmitter(change3.getChangeId(), 2);
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
    RevCommit head = getRemoteHead();
    assertEquals(oldHead, head);
    assertCurrentRevision(change2.getChangeId(), 1, change2.getCommitId());
    assertSubmitter(change2.getChangeId(), 1);
  }

  @Test
  public void submitChangeWithOutdatedDependency() throws Exception {
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

  @Test
  public void submitChangeWithOutdatedConflictingDependency() throws Exception {
    Git git = createProject();
    PushOneCommit.Result change1 =
        createChange(git, "Change 1", "a.txt", "content");

    PushOneCommit.Result change2 =
        createChange(git, "Change 2", "b.txt", "other content");

    checkout(git, change1.getCommit().getName());
    PushOneCommit.Result change1a =
        createChange(git, "Change 1 (amended)", "b.txt", "conflicting things here",
            change1.getChangeId());
    submit(change1a.getChangeId());

    RevCommit oldHead = getRemoteHead();
    submitWithConflict(change2.getChangeId());
    assertEquals(getRemoteHead(), oldHead);
  }
}
