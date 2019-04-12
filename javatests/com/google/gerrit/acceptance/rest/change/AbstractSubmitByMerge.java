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
import static com.google.common.truth.TruthJUnit.assume;

import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestProjectInput;
import com.google.gerrit.extensions.client.InheritableBoolean;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

public abstract class AbstractSubmitByMerge extends AbstractSubmit {

  @Test
  public void submitWithMerge() throws Exception {
    RevCommit initialHead = getRemoteHead();
    PushOneCommit.Result change = createChange("Change 1", "a.txt", "content");
    submit(change.getChangeId());

    RevCommit oldHead = getRemoteHead();
    testRepo.reset(initialHead);
    PushOneCommit.Result change2 = createChange("Change 2", "b.txt", "other content");
    submit(change2.getChangeId());
    RevCommit head = getRemoteHead();
    assertThat(head.getParentCount()).isEqualTo(2);
    assertThat(head.getParent(0)).isEqualTo(oldHead);
    assertThat(head.getParent(1)).isEqualTo(change2.getCommit());
  }

  @Test
  @TestProjectInput(useContentMerge = InheritableBoolean.TRUE)
  public void submitWithContentMerge() throws Exception {
    PushOneCommit.Result change = createChange("Change 1", "a.txt", "aaa\nbbb\nccc\n");
    submit(change.getChangeId());
    PushOneCommit.Result change2 = createChange("Change 2", "a.txt", "aaa\nbbb\nccc\nddd\n");
    submit(change2.getChangeId());

    RevCommit oldHead = getRemoteHead();
    testRepo.reset(change.getCommit());
    PushOneCommit.Result change3 = createChange("Change 3", "a.txt", "bbb\nccc\n");
    submit(change3.getChangeId());
    RevCommit head = getRemoteHead();
    assertThat(head.getParentCount()).isEqualTo(2);
    assertThat(head.getParent(0)).isEqualTo(oldHead);
    assertThat(head.getParent(1)).isEqualTo(change3.getCommit());
  }

  @Test
  @TestProjectInput(useContentMerge = InheritableBoolean.TRUE)
  public void submitWithContentMerge_Conflict() throws Exception {
    RevCommit initialHead = getRemoteHead();
    PushOneCommit.Result change = createChange("Change 1", "a.txt", "content");
    submit(change.getChangeId());

    RevCommit oldHead = getRemoteHead();
    testRepo.reset(initialHead);
    PushOneCommit.Result change2 = createChange("Change 2", "a.txt", "other content");
    submitWithConflict(
        change2.getChangeId(),
        "Failed to submit 1 change due to the following problems:\n"
            + "Change "
            + change2.getChange().getId()
            + ": "
            + "Change could not be merged due to a path conflict. "
            + "Please rebase the change locally "
            + "and upload the rebased commit for review.");
    assertThat(getRemoteHead()).isEqualTo(oldHead);
  }

  @Test
  @TestProjectInput(createEmptyCommit = false)
  public void submitMultipleCommitsToEmptyRepoAsFastForward() throws Exception {
    PushOneCommit.Result change1 = createChange();
    PushOneCommit.Result change2 = createChange();
    approve(change1.getChangeId());
    submit(change2.getChangeId());
    assertThat(getRemoteHead().getId()).isEqualTo(change2.getCommit());
  }

  @Test
  @TestProjectInput(createEmptyCommit = false)
  public void submitMultipleCommitsToEmptyRepoWithOneMerge() throws Exception {
    assume().that(isSubmitWholeTopicEnabled()).isTrue();
    PushOneCommit.Result change1 =
        pushFactory
            .create(admin.newIdent(), testRepo, "Change 1", "a", "a")
            .to("refs/for/master/" + name("topic"));

    PushOneCommit push2 = pushFactory.create(admin.newIdent(), testRepo, "Change 2", "b", "b");
    push2.noParents();
    PushOneCommit.Result change2 = push2.to("refs/for/master/" + name("topic"));
    change2.assertOkStatus();

    approve(change1.getChangeId());
    submit(change2.getChangeId());

    RevCommit head = getRemoteHead();
    assertThat(head.getParents()).hasLength(2);
    assertThat(head.getParent(0)).isEqualTo(change1.getCommit());
    assertThat(head.getParent(1)).isEqualTo(change2.getCommit());
  }
}
