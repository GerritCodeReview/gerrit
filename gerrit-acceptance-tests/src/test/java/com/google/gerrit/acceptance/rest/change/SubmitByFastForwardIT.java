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
import com.google.gerrit.extensions.common.ProjectSubmitType;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

public class SubmitByFastForwardIT extends AbstractSubmit {

  @Override
  protected ProjectSubmitType getSubmitType() {
    return ProjectSubmitType.FAST_FORWARD_ONLY;
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

  @Test
  public void submitFastForwardNotPossible_Conflict() throws Exception {
    Git git = createProject();
    RevCommit initialHead = getRemoteHead();
    PushOneCommit.Result change =
        createChange(git, "Change 1", "a.txt", "content");
    submit(change.getChangeId());

    RevCommit oldHead = getRemoteHead();
    checkout(git, initialHead.getId().getName());
    PushOneCommit.Result change2 =
        createChange(git, "Change 2", "b.txt", "other content");
    submitWithConflict(change2.getChangeId());
    assertEquals(oldHead, getRemoteHead());
    assertSubmitter(change.getChangeId(), 1);
  }
}
