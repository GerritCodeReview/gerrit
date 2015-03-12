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

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.GitUtil.checkout;

import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestProjectInput;
import com.google.gerrit.extensions.client.InheritableBoolean;

import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

public abstract class AbstractSubmitByMerge extends AbstractSubmit {

  @Test
  public void submitWithMerge() throws Exception {
    RevCommit initialHead = getRemoteHead();
    PushOneCommit.Result change =
        createChange(git, "Change 1", "a.txt", "content");
    submit(change.getChangeId());

    RevCommit oldHead = getRemoteHead();
    checkout(git, initialHead.getId().getName());
    PushOneCommit.Result change2 =
        createChange(git, "Change 2", "b.txt", "other content");
    submit(change2.getChangeId());
    RevCommit head = getRemoteHead();
    assertThat(head.getParentCount()).isEqualTo(2);
    assertThat(head.getParent(0)).isEqualTo(oldHead);
    assertThat(head.getParent(1)).isEqualTo(change2.getCommitId());
  }

  @Test
  @TestProjectInput(useContentMerge = InheritableBoolean.TRUE)
  public void submitWithContentMerge() throws Exception {
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
    RevCommit head = getRemoteHead();
    assertThat(head.getParentCount()).isEqualTo(2);
    assertThat(head.getParent(0)).isEqualTo(oldHead);
    assertThat(head.getParent(1)).isEqualTo(change3.getCommitId());
  }

  @Test
  @TestProjectInput(useContentMerge = InheritableBoolean.TRUE)
  public void submitWithContentMerge_Conflict() throws Exception {
    RevCommit initialHead = getRemoteHead();
    PushOneCommit.Result change =
        createChange(git, "Change 1", "a.txt", "content");
    submit(change.getChangeId());

    RevCommit oldHead = getRemoteHead();
    checkout(git, initialHead.getId().getName());
    PushOneCommit.Result change2 =
        createChange(git, "Change 2", "a.txt", "other content");
    submitWithConflict(change2.getChangeId());
    assertThat(getRemoteHead()).isEqualTo(oldHead);
  }
}
