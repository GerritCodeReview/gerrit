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
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestProjectInput;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.server.change.MergeabilityComputationBehavior;
import com.google.inject.Inject;
import java.io.ByteArrayOutputStream;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

public abstract class AbstractSubmitByMerge extends AbstractSubmit {
  @Inject private ProjectOperations projectOperations;

  @Test
  public void submitWithMerge() throws Throwable {
    RevCommit initialHead = projectOperations.project(project).getHead("master");
    PushOneCommit.Result change = createChange("Change 1", "a.txt", "content");
    submit(change.getChangeId());

    RevCommit oldHead = projectOperations.project(project).getHead("master");
    testRepo.reset(initialHead);
    PushOneCommit.Result change2 = createChange("Change 2", "b.txt", "other content");
    submit(change2.getChangeId());
    RevCommit head = projectOperations.project(project).getHead("master");
    assertThat(head.getParentCount()).isEqualTo(2);
    assertThat(head.getParent(0)).isEqualTo(oldHead);
    assertThat(head.getParent(1)).isEqualTo(change2.getCommit());
  }

  @Test
  @TestProjectInput(useContentMerge = InheritableBoolean.TRUE)
  public void submitWithContentMerge() throws Throwable {
    PushOneCommit.Result change = createChange("Change 1", "a.txt", "aaa\nbbb\nccc\n");
    submit(change.getChangeId());
    PushOneCommit.Result change2 = createChange("Change 2", "a.txt", "aaa\nbbb\nccc\nddd\n");
    submit(change2.getChangeId());

    RevCommit oldHead = projectOperations.project(project).getHead("master");
    testRepo.reset(change.getCommit());
    PushOneCommit.Result change3 = createChange("Change 3", "a.txt", "bbb\nccc\n");
    submit(change3.getChangeId());
    RevCommit head = projectOperations.project(project).getHead("master");
    assertThat(head.getParentCount()).isEqualTo(2);
    assertThat(head.getParent(0)).isEqualTo(oldHead);
    assertThat(head.getParent(1)).isEqualTo(change3.getCommit());
  }

  @Test
  @TestProjectInput(useContentMerge = InheritableBoolean.TRUE)
  public void submitWithContentMerge_Conflict() throws Throwable {
    RevCommit initialHead = projectOperations.project(project).getHead("master");
    PushOneCommit.Result change = createChange("Change 1", "a.txt", "content");
    submit(change.getChangeId());

    RevCommit oldHead = projectOperations.project(project).getHead("master");
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
    assertThat(projectOperations.project(project).getHead("master")).isEqualTo(oldHead);
  }

  @Test
  @TestProjectInput(useContentMerge = InheritableBoolean.TRUE)
  @GerritConfig(name = "core.useGitattributesForMerge", value = "true")
  public void submitWithUnionContentMerge() throws Throwable {
    PushOneCommit pushAttributes =
        pushFactory.create(
            admin.newIdent(),
            testRepo,
            "add merge=union to gitattributes",
            ".gitattributes",
            "*.txt merge=union");
    PushOneCommit.Result unusedResult = pushAttributes.to("refs/heads/master");
    RevCommit initialHead = projectOperations.project(project).getHead("master");
    PushOneCommit.Result change = createChange("Change 1", "a.txt", "content");
    submit(change.getChangeId());

    RevCommit oldHead = projectOperations.project(project).getHead("master");
    testRepo.reset(initialHead);
    PushOneCommit.Result change2 = createChange("Change 2", "a.txt", "other content");
    submit(change2.getChangeId());
    RevCommit head = projectOperations.project(project).getHead("master");
    assertThat(head.getParentCount()).isEqualTo(2);
    assertThat(head.getParent(0)).isEqualTo(oldHead);
    assertThat(head.getParent(1)).isEqualTo(change2.getCommit());

    // We expect that it has no conflict markers and the content of both changes.
    BinaryResult bin = gApi.projects().name(project.get()).branch("master").file("a.txt");
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    bin.writeTo(os);
    String fileContent = new String(os.toByteArray(), UTF_8);
    assertThat(fileContent).isEqualTo("content" + "\n" + "other content");
  }

  @Test
  @TestProjectInput(createEmptyCommit = false)
  public void submitMultipleCommitsToEmptyRepoAsFastForward() throws Throwable {
    PushOneCommit.Result change1 = createChange();
    PushOneCommit.Result change2 = createChange();
    approve(change1.getChangeId());
    submit(change2.getChangeId());
    assertThat(projectOperations.project(project).getHead("master").getId())
        .isEqualTo(change2.getCommit());
  }

  @Test
  @TestProjectInput(createEmptyCommit = false)
  public void submitMultipleCommitsToEmptyRepoWithOneMerge() throws Throwable {
    assume().that(isSubmitWholeTopicEnabled()).isTrue();
    PushOneCommit.Result change1 =
        pushFactory
            .create(admin.newIdent(), testRepo, "Change 1", "a", "a")
            .to("refs/for/master%topic=" + name("topic"));

    PushOneCommit push2 = pushFactory.create(admin.newIdent(), testRepo, "Change 2", "b", "b");
    push2.noParents();
    PushOneCommit.Result change2 = push2.to("refs/for/master%topic=" + name("topic"));
    change2.assertOkStatus();

    approve(change1.getChangeId());
    submit(change2.getChangeId());

    RevCommit head = projectOperations.project(project).getHead("master");
    assertThat(head.getParents()).hasLength(2);
    assertThat(head.getParent(0)).isEqualTo(change1.getCommit());
    assertThat(head.getParent(1)).isEqualTo(change2.getCommit());
  }

  @Test
  public void dependencyOnOutdatedPatchSetOfUnsubmittedChangePreventsMerge() throws Throwable {
    // Create a change
    PushOneCommit change = pushFactory.create(user.newIdent(), testRepo, "fix", "a.txt", "foo");
    PushOneCommit.Result changeResult = change.to("refs/for/master");
    PatchSet.Id patchSetId = changeResult.getPatchSetId();

    // Create a successor change.
    PushOneCommit change2 =
        pushFactory.create(user.newIdent(), testRepo, "feature", "b.txt", "bar");
    PushOneCommit.Result change2Result = change2.to("refs/for/master");

    // Create new patch set for first change.
    testRepo.reset(changeResult.getCommit().name());
    amendChange(changeResult.getChangeId());

    // Approve both changes
    approve(changeResult.getChangeId());
    approve(change2Result.getChangeId());

    // submit button is disabled.
    if (mcb != MergeabilityComputationBehavior.NEVER) {
      assertSubmitDisabled(change2Result.getChangeId());
    }

    submitWithConflict(
        change2Result.getChangeId(),
        "Failed to submit 2 changes due to the following problems:\n"
            + "Change "
            + change2Result.getChange().getId()
            + ": Depends on commit that cannot be merged."
            + " Commit "
            + change2Result.getCommit().name()
            + " depends on commit "
            + changeResult.getCommit().name()
            + ", which is outdated patch set "
            + patchSetId.get()
            + " of change "
            + changeResult.getChange().getId()
            + ". The latest patch set is "
            + changeResult.getPatchSetId().get()
            + ".");

    assertRefUpdatedEvents();
    assertChangeMergedEvents();
  }

  @Test
  public void dependencyOnOutdatedPatchSetOfSubmittedChangePreventsMerge() throws Throwable {
    RevCommit initialHead = projectOperations.project(project).getHead("master");

    // Create a change
    PushOneCommit change = pushFactory.create(user.newIdent(), testRepo, "fix", "a.txt", "foo");
    PushOneCommit.Result changeResult = change.to("refs/for/master");
    PatchSet.Id patchSetId = changeResult.getPatchSetId();

    // Create a successor change.
    PushOneCommit change2 =
        pushFactory.create(user.newIdent(), testRepo, "feature", "b.txt", "bar");
    PushOneCommit.Result change2Result = change2.to("refs/for/master");

    // Create new patch set for first change.
    testRepo.reset(changeResult.getCommit().name());
    amendChange(changeResult.getChangeId());

    // Approve and submit the first changes
    approve(changeResult.getChangeId());
    submit(changeResult.getChangeId());
    RevCommit headAfterSubmit = projectOperations.project(project).getHead("master");

    // Approve the second change
    approve(change2Result.getChangeId());

    // submit button is disabled.
    if (mcb != MergeabilityComputationBehavior.NEVER) {
      assertSubmitDisabled(change2Result.getChangeId());
    }

    submitWithConflict(
        change2Result.getChangeId(),
        "Failed to submit 1 change due to the following problems:\n"
            + "Change "
            + change2Result.getChange().getId()
            + ": Depends on commit that cannot be merged."
            + " Commit "
            + change2Result.getCommit().name()
            + " depends on commit "
            + changeResult.getCommit().name()
            + ", which is outdated patch set "
            + patchSetId.get()
            + " of change "
            + changeResult.getChange().getId()
            + ". The latest patch set is "
            + changeResult.getPatchSetId().get()
            + ".");

    // Only events for the first change are sent.
    assertRefUpdatedEvents(initialHead, headAfterSubmit);
    assertChangeMergedEvents(changeResult.getChangeId(), headAfterSubmit.name());
  }
}
