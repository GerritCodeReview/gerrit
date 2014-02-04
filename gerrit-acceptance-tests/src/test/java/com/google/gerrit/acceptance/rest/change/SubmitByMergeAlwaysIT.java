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
import com.google.gwtorm.server.OrmException;

import com.jcraft.jsch.JSchException;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

public class SubmitByMergeAlwaysIT extends AbstractSubmitByMerge {

  @Override
  protected ProjectSubmitType getSubmitType() {
    return ProjectSubmitType.MERGE_ALWAYS;
  }

  @Test
  public void submitWithMergeIfFastForwardPossible() throws Exception {
    Git git = createProject();
    RevCommit oldHead = getRemoteHead();
    PushOneCommit.Result change = createChange(git);
    submit(change.getChangeId());
    RevCommit head = getRemoteHead();
    assertEquals(2, head.getParentCount());
    assertEquals(oldHead, head.getParent(0));
    assertEquals(change.getCommitId(), head.getParent(1));
    assertSubmitter(change.getChangeId(), 1);
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
        tip.getParent(1).getShortMessage());

    assertEquals(initialHead.getId(), tip.getParent(0).getId());
  }
}
