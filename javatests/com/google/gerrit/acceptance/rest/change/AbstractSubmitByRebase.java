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
import static com.google.gerrit.acceptance.GitUtil.getChangeId;
import static com.google.gerrit.acceptance.GitUtil.pushHead;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowLabel;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.block;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.TestProjectInput;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.server.change.MergeabilityComputationBehavior;
import com.google.gerrit.server.project.testing.TestLabels;
import com.google.inject.Inject;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Test;

public abstract class AbstractSubmitByRebase extends AbstractSubmit {
  @Inject private ProjectOperations projectOperations;
  @Inject private RequestScopeOperations requestScopeOperations;

  @Override
  protected abstract SubmitType getSubmitType();

  @Test
  @TestProjectInput(useContentMerge = InheritableBoolean.TRUE)
  public void submitWithRebase() throws Throwable {
    submitWithRebase(admin);
  }

  @Test
  @TestProjectInput(useContentMerge = InheritableBoolean.TRUE)
  public void submitWithRebaseWithoutAddPatchSetPermission() throws Throwable {
    projectOperations
        .project(project)
        .forUpdate()
        .add(block(Permission.ADD_PATCH_SET).ref("refs/*").group(REGISTERED_USERS))
        .add(allow(Permission.SUBMIT).ref("refs/heads/*").group(REGISTERED_USERS))
        .add(
            allowLabel(TestLabels.codeReview().getName())
                .ref("refs/heads/*")
                .group(REGISTERED_USERS)
                .range(-2, 2))
        .update();

    submitWithRebase(user);
  }

  protected ImmutableList<PushOneCommit.Result> submitWithRebase(TestAccount submitter)
      throws Throwable {
    requestScopeOperations.setApiUser(submitter.id());
    RevCommit initialHead = projectOperations.project(project).getHead("master");
    PushOneCommit.Result change = createChange("Change 1", "a.txt", "content");
    submit(change.getChangeId());

    RevCommit headAfterFirstSubmit = projectOperations.project(project).getHead("master");
    testRepo.reset(initialHead);
    PushOneCommit.Result change2 = createChange("Change 2", "b.txt", "other content");
    submit(change2.getChangeId());
    assertRebase(testRepo, false);
    RevCommit headAfterSecondSubmit = projectOperations.project(project).getHead("master");
    assertThat(headAfterSecondSubmit.getParent(0)).isEqualTo(headAfterFirstSubmit);
    assertApproved(change2.getChangeId(), submitter);
    assertCurrentRevision(change2.getChangeId(), 2, headAfterSecondSubmit);
    assertSubmitter(change2.getChangeId(), 1, submitter);
    assertSubmitter(change2.getChangeId(), 2, submitter);
    assertPersonEquals(admin.newIdent(), headAfterSecondSubmit.getAuthorIdent());
    assertPersonEquals(submitter.newIdent(), headAfterSecondSubmit.getCommitterIdent());

    assertRefUpdatedEvents(
        initialHead, headAfterFirstSubmit, headAfterFirstSubmit, headAfterSecondSubmit);
    assertChangeMergedEvents(
        change.getChangeId(),
        headAfterFirstSubmit.name(),
        change2.getChangeId(),
        headAfterSecondSubmit.name());
    return ImmutableList.of(change, change2);
  }

  @Test
  public void submitWithRebaseMultipleChanges() throws Throwable {
    RevCommit initialHead = projectOperations.project(project).getHead("master");
    PushOneCommit.Result change1 = createChange("Change 1", "a.txt", "content");
    submit(change1.getChangeId());
    RevCommit headAfterFirstSubmit = projectOperations.project(project).getHead("master");
    if (getSubmitType() == SubmitType.REBASE_ALWAYS) {
      assertCurrentRevision(change1.getChangeId(), 2, headAfterFirstSubmit);
    } else {
      assertThat(headAfterFirstSubmit.name()).isEqualTo(change1.getCommit().name());
    }

    testRepo.reset(initialHead);
    PushOneCommit.Result change2 = createChange("Change 2", "b.txt", "other content");
    assertThat(change2.getCommit().getParent(0)).isNotEqualTo(change1.getCommit());
    PushOneCommit.Result change3 = createChange("Change 3", "c.txt", "third content");
    PushOneCommit.Result change4 = createChange("Change 4", "d.txt", "fourth content");
    approve(change2.getChangeId());
    approve(change3.getChangeId());
    submit(change4.getChangeId());

    assertRebase(testRepo, false);
    assertApproved(change2.getChangeId());
    assertApproved(change3.getChangeId());
    assertApproved(change4.getChangeId());

    RevCommit headAfterSecondSubmit = parse(projectOperations.project(project).getHead("master"));
    assertThat(headAfterSecondSubmit.getShortMessage()).isEqualTo("Change 4");
    assertThat(headAfterSecondSubmit).isNotEqualTo(change4.getCommit());
    assertCurrentRevision(change4.getChangeId(), 2, headAfterSecondSubmit);

    RevCommit parent = parse(headAfterSecondSubmit.getParent(0));
    assertThat(parent.getShortMessage()).isEqualTo("Change 3");
    assertThat(parent).isNotEqualTo(change3.getCommit());
    assertCurrentRevision(change3.getChangeId(), 2, parent);

    RevCommit grandparent = parse(parent.getParent(0));
    assertThat(grandparent).isNotEqualTo(change2.getCommit());
    assertCurrentRevision(change2.getChangeId(), 2, grandparent);

    RevCommit greatgrandparent = parse(grandparent.getParent(0));
    assertThat(greatgrandparent).isEqualTo(headAfterFirstSubmit);
    if (getSubmitType() == SubmitType.REBASE_ALWAYS) {
      assertCurrentRevision(change1.getChangeId(), 2, greatgrandparent);
    } else {
      assertCurrentRevision(change1.getChangeId(), 1, greatgrandparent);
    }

    assertRefUpdatedEvents(
        initialHead, headAfterFirstSubmit, headAfterFirstSubmit, headAfterSecondSubmit);
    assertChangeMergedEvents(
        change1.getChangeId(),
        headAfterFirstSubmit.name(),
        change2.getChangeId(),
        headAfterSecondSubmit.name(),
        change3.getChangeId(),
        headAfterSecondSubmit.name(),
        change4.getChangeId(),
        headAfterSecondSubmit.name());
  }

  @Test
  public void submitMergeCommitThatDependsOnNormalChangeViaTheFirstParent() throws Throwable {
    /*
         *  change2 (merge, rebased)
         | \
         *  \  change1 (rebased)
         |   |
         *   | change3 (new tip, rebased if 'Merge Always')
         |   |
         | * | change2 (merge)
         | |\|
         | | |
         | * | change1
          \|/
           * initialHead
    */
    RevCommit initialHead = projectOperations.project(project).getHead("master");
    PushOneCommit.Result change1 = createChange("Added a", "a.txt", "");

    PushOneCommit change2Push =
        pushFactory.create(admin.newIdent(), testRepo, "Merge to master", "m.txt", "");
    change2Push.setParents(ImmutableList.of(change1.getCommit(), initialHead));
    PushOneCommit.Result change2 = change2Push.to("refs/for/master");

    testRepo.reset(initialHead);
    PushOneCommit.Result change3 = createChange("New tip", "b.txt", "");

    approve(change3.getChangeId());
    submit(change3.getChangeId());

    approve(change1.getChangeId());
    approve(change2.getChangeId());
    submit(change2.getChangeId());

    RevCommit newHead = projectOperations.project(project).getHead("master");
    assertThat(newHead.getParentCount()).isEqualTo(2);

    RevCommit headParent1 = parse(newHead.getParent(0).getId());
    RevCommit headParent2 = parse(newHead.getParent(1).getId());

    assertCurrentRevision(change1.getChangeId(), 2, headParent1.getId());
    assertThat(headParent2.getId()).isEqualTo(initialHead.getId());

    assertThat(headParent1.getParentCount()).isEqualTo(1);
    RevCommit headGrandparent1 = parse(headParent1.getParent(0).getId());
    if (getSubmitType() == SubmitType.REBASE_ALWAYS) {
      assertCurrentRevision(change3.getChangeId(), 2, headGrandparent1.getId());
    } else {
      assertThat(change3.getCommit().getId()).isEqualTo(headGrandparent1.getId());
    }

    assertThat(headGrandparent1.getParentCount()).isEqualTo(1);
    assertThat(headGrandparent1.getParent(0).getId()).isEqualTo(initialHead.getId());
  }

  @Test
  public void submitMergeCommitThatDependsOnNormalChangeViaTheSecondParent() throws Throwable {
    /*
       *  change2 (merge, rebased)
       | \
       *  \  change3 (new tip, rebased if 'Rebase Always')
       |   |
       | * | change2 (merge)
       | |\|
       | | * change1
       | |/
       | |
       |/
       * initialHead
    */
    RevCommit initialHead = projectOperations.project(project).getHead("master");
    PushOneCommit.Result change1 = createChange("Added a", "a.txt", "");

    PushOneCommit change2Push =
        pushFactory.create(admin.newIdent(), testRepo, "Merge to master", "m.txt", "");
    change2Push.setParents(ImmutableList.of(initialHead, change1.getCommit()));
    PushOneCommit.Result change2 = change2Push.to("refs/for/master");

    testRepo.reset(initialHead);
    PushOneCommit.Result change3 = createChange("New tip", "b.txt", "");

    approve(change3.getChangeId());
    submit(change3.getChangeId());

    approve(change1.getChangeId());
    approve(change2.getChangeId());
    submit(change2.getChangeId());

    RevCommit newHead = projectOperations.project(project).getHead("master");
    assertThat(newHead.getParentCount()).isEqualTo(2);

    RevCommit headParent1 = parse(newHead.getParent(0).getId());
    RevCommit headParent2 = parse(newHead.getParent(1).getId());

    if (getSubmitType() == SubmitType.REBASE_ALWAYS) {
      assertCurrentRevision(change3.getChangeId(), 2, headParent1.getId());
    } else {
      assertThat(change3.getCommit().getId()).isEqualTo(headParent1.getId());
    }
    assertThat(headParent1.getParentCount()).isEqualTo(1);
    assertThat(headParent1.getParent(0)).isEqualTo(initialHead);

    assertThat(headParent2.getId()).isEqualTo(change1.getCommit().getId());
    assertThat(headParent2.getParentCount()).isEqualTo(1);

    RevCommit headGrandparent = parse(headParent2.getParent(0).getId());

    assertThat(headGrandparent.getId()).isEqualTo(initialHead.getId());
  }

  @Test
  @TestProjectInput(useContentMerge = InheritableBoolean.TRUE)
  public void submitWithContentMerge_Conflict() throws Throwable {
    RevCommit initialHead = projectOperations.project(project).getHead("master");
    PushOneCommit.Result change = createChange("Change 1", "a.txt", "content");
    submit(change.getChangeId());

    RevCommit headAfterFirstSubmit = projectOperations.project(project).getHead("master");
    testRepo.reset(initialHead);
    PushOneCommit.Result change2 = createChange("Change 2", "a.txt", "other content");
    submitWithConflict(
        change2.getChangeId(),
        String.format(
            """
            Cannot rebase %s: Change %s could not be rebased due to a conflict during merge.

            merge conflict(s):
            * a.txt
            """,
            change2.getCommit().name(), change2.getChange().getId()));
    RevCommit head = projectOperations.project(project).getHead("master");
    assertThat(head).isEqualTo(headAfterFirstSubmit);
    assertCurrentRevision(change2.getChangeId(), 1, change2.getCommit());
    assertNoSubmitter(change2.getChangeId(), 1);

    assertRefUpdatedEvents(initialHead, headAfterFirstSubmit);
    assertChangeMergedEvents(change.getChangeId(), headAfterFirstSubmit.name());
  }

  protected RevCommit parse(ObjectId id) throws Throwable {
    try (Repository repo = repoManager.openRepository(project);
        RevWalk rw = new RevWalk(repo)) {
      RevCommit c = rw.parseCommit(id);
      rw.parseBody(c);
      return c;
    }
  }

  @Test
  public void submitAfterReorderOfCommits() throws Throwable {
    RevCommit initialHead = projectOperations.project(project).getHead("master");

    // Create two commits and push.
    RevCommit c1 = commitBuilder().add("a.txt", "1").message("subject: 1").create();
    RevCommit c2 = commitBuilder().add("b.txt", "2").message("subject: 2").create();
    pushHead(testRepo, "refs/for/master", false);

    String id1 = getChangeId(testRepo, c1).get();
    String id2 = getChangeId(testRepo, c2).get();

    // Swap the order of commits and push again.
    testRepo.reset("HEAD~2");
    testRepo.cherryPick(c2);
    testRepo.cherryPick(c1);
    pushHead(testRepo, "refs/for/master", false);

    approve(id1);
    approve(id2);
    submit(id1);
    RevCommit headAfterSubmit = projectOperations.project(project).getHead("master");

    assertRefUpdatedEvents(initialHead, headAfterSubmit);
    assertChangeMergedEvents(id2, headAfterSubmit.name(), id1, headAfterSubmit.name());
  }

  @Test
  public void submitChangesAfterBranchOnSecond() throws Throwable {
    RevCommit initialHead = projectOperations.project(project).getHead("master");

    PushOneCommit.Result change = createChange();
    approve(change.getChangeId());

    PushOneCommit.Result change2 = createChange();
    approve(change2.getChangeId());
    Project.NameKey project = change2.getChange().change().getProject();
    BranchNameKey branch = BranchNameKey.create(project, "branch");
    createBranchWithRevision(branch, change2.getCommit().getName());
    gApi.changes().id(change2.getChangeId()).current().submit();
    assertMerged(change2.getChangeId());
    assertMerged(change.getChangeId());

    RevCommit newHead = projectOperations.project(this.project).getHead("master");
    assertRefUpdatedEvents(initialHead, newHead);
    assertChangeMergedEvents(
        change.getChangeId(), newHead.name(), change2.getChangeId(), newHead.name());
  }

  @Test
  @TestProjectInput(useContentMerge = InheritableBoolean.TRUE)
  public void submitFastForwardIdenticalTree() throws Throwable {
    RevCommit initialHead = projectOperations.project(project).getHead("master");
    PushOneCommit.Result change1 = createChange("Change 1", "a.txt", "a");
    PushOneCommit.Result change2 = createChange("Change 2", "a.txt", "a");

    assertThat(change1.getCommit().getTree()).isEqualTo(change2.getCommit().getTree());

    // for rebase if necessary, otherwise, the manual rebase of change2 will
    // fail since change1 would be merged as fast forward
    testRepo.reset(initialHead);
    PushOneCommit.Result change0 = createChange("Change 0", "b.txt", "b");
    submit(change0.getChangeId());
    RevCommit headAfterChange0 = projectOperations.project(project).getHead("master");
    assertThat(headAfterChange0.getShortMessage()).isEqualTo("Change 0");

    submit(change1.getChangeId());
    RevCommit headAfterChange1 = projectOperations.project(project).getHead("master");
    assertThat(headAfterChange1.getShortMessage()).isEqualTo("Change 1");
    assertThat(headAfterChange0).isEqualTo(headAfterChange1.getParent(0));

    // Do manual rebase first.
    gApi.changes().id(change2.getChangeId()).current().rebase();
    submit(change2.getChangeId());
    RevCommit headAfterChange2 = projectOperations.project(project).getHead("master");
    assertThat(headAfterChange2.getShortMessage()).isEqualTo("Change 2");
    assertThat(headAfterChange1).isEqualTo(headAfterChange2.getParent(0));

    ChangeInfo info2 = info(change2.getChangeId());
    assertThat(info2.status).isEqualTo(ChangeStatus.MERGED);
  }

  @Test
  @TestProjectInput(useContentMerge = InheritableBoolean.TRUE)
  public void submitChainOneByOne() throws Throwable {
    PushOneCommit.Result change1 = createChange("subject 1", "fileName 1", "content 1");
    PushOneCommit.Result change2 = createChange("subject 2", "fileName 2", "content 2");
    submit(change1.getChangeId());
    submit(change2.getChangeId());
  }

  @Test
  @TestProjectInput(useContentMerge = InheritableBoolean.TRUE)
  public void submitChainFailsOnRework() throws Throwable {
    PushOneCommit.Result change1 = createChange("subject 1", "fileName 1", "content 1");
    RevCommit headAfterChange1 = change1.getCommit();
    PushOneCommit.Result change2 = createChange("subject 2", "fileName 2", "content 2");
    testRepo.reset(headAfterChange1);
    change1 =
        amendChange(change1.getChangeId(), "subject 1 amend", "fileName 2", "rework content 2");
    submit(change1.getChangeId());
    headAfterChange1 = projectOperations.project(project).getHead("master");

    submitWithConflict(
        change2.getChangeId(),
        String.format(
            """
            Cannot rebase %s: Change %s could not be rebased due to a conflict during merge.

            merge conflict(s):
            * fileName 2
            """,
            change2.getCommit().name(), change2.getChange().getId()));
    assertThat(projectOperations.project(project).getHead("master")).isEqualTo(headAfterChange1);
  }

  @Test
  @TestProjectInput(useContentMerge = InheritableBoolean.TRUE)
  public void submitChainOneByOneManualRebase() throws Throwable {
    RevCommit initialHead = projectOperations.project(project).getHead("master");
    PushOneCommit.Result change1 = createChange("subject 1", "fileName 1", "content 1");
    PushOneCommit.Result change2 = createChange("subject 2", "fileName 2", "content 2");

    // for rebase if necessary, otherwise, the manual rebase of change2 will
    // fail since change1 would be merged as fast forward
    testRepo.reset(initialHead);
    PushOneCommit.Result change = createChange();
    submit(change.getChangeId());

    submit(change1.getChangeId());
    // Do manual rebase first.
    gApi.changes().id(change2.getChangeId()).current().rebase();
    submit(change2.getChangeId());
  }

  @Test
  public void dependencyOnOutdatedPatchSetPreventsRebase() throws Throwable {
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
  @TestProjectInput(useContentMerge = InheritableBoolean.TRUE)
  public void submitStackedChanges() throws Throwable {
    PushOneCommit.Result change1 = createChange("Change 1", "a.txt", "content 1");
    PushOneCommit.Result change2 = createChange("Change 2", "a.txt", "content 1\ncontent 2");

    assertThat(gApi.changes().id(change1.getChangeId()).current().mergeable().mergeable).isTrue();
    assertThat(gApi.changes().id(change2.getChangeId()).current().mergeable().mergeable).isTrue();

    approve(change1.getChangeId());
    submit(change1.getChangeId());

    assertThat(gApi.changes().id(change2.getChangeId()).current().mergeable().mergeable).isTrue();

    approve(change2.getChangeId());
    submit(change2.getChangeId());

    verifyStackedSubmissionHistory(change1, change2, /* hasConcurrentCommit= */ false);
  }

  @Test
  @TestProjectInput(useContentMerge = InheritableBoolean.TRUE)
  public void submitStackedChangesWithConcurrentCommit() throws Throwable {
    RevCommit initialHead = projectOperations.project(project).getHead("master");

    // Create stacked changes Change 1 -> Change 2
    PushOneCommit.Result change1 = createChange("Change 1", "a.txt", "content 1");
    PushOneCommit.Result change2 = createChange("Change 2", "a.txt", "content 1\ncontent 2");

    // Push a non-conflicting concurrent commit directly to master
    testRepo.reset(initialHead);
    PushOneCommit.Result concurrent =
        createChange("Concurrent Commit", "b.txt", "concurrent content");
    approve(concurrent.getChangeId());
    submit(concurrent.getChangeId());

    // Both changes must still report as mergeable
    assertThat(gApi.changes().id(change1.getChangeId()).current().mergeable().mergeable).isTrue();
    assertThat(gApi.changes().id(change2.getChangeId()).current().mergeable().mergeable).isTrue();

    // Submit Change 1 (forces rebase under both strategies)
    approve(change1.getChangeId());
    submit(change1.getChangeId());

    // Stacked child Change 2 must remain mergeable
    assertThat(gApi.changes().id(change2.getChangeId()).current().mergeable().mergeable).isTrue();

    // Submit Change 2 (forces rebase)
    approve(change2.getChangeId());
    submit(change2.getChangeId());

    verifyStackedSubmissionHistory(change1, change2, /* hasConcurrentCommit= */ true);
  }

  @Test
  @TestProjectInput(useContentMerge = InheritableBoolean.TRUE)
  public void submitStackedChangesTogether() throws Throwable {
    RevCommit initialHead = projectOperations.project(project).getHead("master");

    PushOneCommit.Result change1 = createChange("Change 1", "a.txt", "content 1");
    PushOneCommit.Result change2 = createChange("Change 2", "a.txt", "content 1\ncontent 2");

    // Push a concurrent commit to master
    testRepo.reset(initialHead);
    PushOneCommit.Result concurrent =
        createChange("Concurrent Commit", "b.txt", "concurrent content");
    approve(concurrent.getChangeId());
    submit(concurrent.getChangeId());

    // Approve both changes
    approve(change1.getChangeId());
    approve(change2.getChangeId());

    // Submit child Change 2 directly. This triggers submission/rebase of both changes in Git order.
    submit(change2.getChangeId());

    verifyStackedSubmissionHistory(change1, change2, /* hasConcurrentCommit= */ true);
  }

  @Test
  @TestProjectInput(useContentMerge = InheritableBoolean.TRUE)
  public void submitStackedChangesWithContentMergeConflict() throws Throwable {
    RevCommit initialHead = projectOperations.project(project).getHead("master");

    PushOneCommit.Result change1 = createChange("Change 1", "a.txt", "content 1");
    PushOneCommit.Result change2 = createChange("Change 2", "a.txt", "content 1\ncontent 2");

    // Push a conflicting concurrent commit directly to master
    testRepo.reset(initialHead);
    PushOneCommit.Result concurrent =
        createChange("Conflicting Commit", "a.txt", "conflicting content");
    approve(concurrent.getChangeId());
    submit(concurrent.getChangeId());

    RevCommit headAfterConcurrent = projectOperations.project(project).getHead("master");

    // Submitting change1 should fail due to rebase/merge conflict
    submitWithConflict(
        change1.getChangeId(),
        String.format(
            """
            Cannot rebase %s: Change %s could not be rebased due to a conflict during merge.

            merge conflict(s):
            * a.txt
            """,
            change1.getCommit().name(), change1.getChange().getId()));

    // Verify master HEAD remains unchanged
    assertThat(projectOperations.project(project).getHead("master")).isEqualTo(headAfterConcurrent);

    // Verify changes remain in NEW status
    assertNew(change1.getChangeId());
    assertNew(change2.getChangeId());
  }

  @Test
  @TestProjectInput(useContentMerge = InheritableBoolean.TRUE)
  public void submitDeeplyStackedChanges() throws Throwable {
    RevCommit initialHead = projectOperations.project(project).getHead("master");

    PushOneCommit.Result change1 = createChange("Change 1", "a.txt", "content 1");
    PushOneCommit.Result change2 = createChange("Change 2", "a.txt", "content 1\ncontent 2");
    PushOneCommit.Result change3 =
        createChange("Change 3", "a.txt", "content 1\ncontent 2\ncontent 3");

    // Push a concurrent commit to master
    testRepo.reset(initialHead);
    PushOneCommit.Result concurrent =
        createChange("Concurrent Commit", "b.txt", "concurrent content");
    approve(concurrent.getChangeId());
    submit(concurrent.getChangeId());

    // Submit grandparent, parent, and child one by one
    approve(change1.getChangeId());
    submit(change1.getChangeId());

    approve(change2.getChangeId());
    submit(change2.getChangeId());

    approve(change3.getChangeId());
    submit(change3.getChangeId());

    // Assert full history structure and lineage
    RevCommit masterHead = parse(projectOperations.project(project).getHead("master"));
    assertThat(masterHead.getShortMessage()).isEqualTo("Change 3");

    RevCommit masterParent = parse(masterHead.getParent(0));
    assertThat(masterParent.getShortMessage()).isEqualTo("Change 2");

    RevCommit masterGrandparent = parse(masterParent.getParent(0));
    assertThat(masterGrandparent.getShortMessage()).isEqualTo("Change 1");

    RevCommit masterGreatGrandparent = parse(masterGrandparent.getParent(0));
    assertThat(masterGreatGrandparent.getShortMessage()).isEqualTo("Concurrent Commit");

    // Both strategies rebase deep chains to Patchset 2 when forced by concurrent commits
    assertCurrentRevision(change3.getChangeId(), 2, masterHead);
    assertCurrentRevision(change2.getChangeId(), 2, masterParent);
    assertCurrentRevision(change1.getChangeId(), 2, masterGrandparent);
  }

  @Test
  public void dryRunRootCommitNotMergeable() throws Throwable {
    // Ensure master branch has at least one commit (initialHead)
    RevCommit initialHead = projectOperations.project(project).getHead("master");

    // Create a root commit (no parents)
    testRepo.reset(initialHead);
    PushOneCommit change = pushFactory.create(admin.newIdent(), testRepo, "Root Commit", "r.txt", "content");
    change.setParents(ImmutableList.of());
    PushOneCommit.Result rootChange = change.to("refs/for/master");
    rootChange.assertOkStatus();

    // Check dryRun: root change must be reported as non-mergeable because it cannot be rebased
    assertThat(gApi.changes().id(rootChange.getChangeId()).current().mergeable().mergeable).isFalse();
  }

  private void verifyStackedSubmissionHistory(
      PushOneCommit.Result change1, PushOneCommit.Result change2, boolean hasConcurrentCommit)
      throws Throwable {
    RevCommit masterHead = parse(projectOperations.project(project).getHead("master"));

    // 1. master HEAD points to the submitted rebased/merged child commit.
    assertThat(masterHead.getShortMessage()).isEqualTo("Change 2");

    // 2. The first parent of master HEAD is the submitted rebased/merged parent commit.
    assertThat(masterHead.getParentCount()).isEqualTo(1);
    RevCommit masterParent = parse(masterHead.getParent(0));
    assertThat(masterParent.getShortMessage()).isEqualTo("Change 1");

    // 3. The parent of the rebased parent commit is the concurrent commit (or initial master
    // commit).
    assertThat(masterParent.getParentCount()).isEqualTo(1);
    RevCommit masterGrandparent = parse(masterParent.getParent(0));

    SubmitType submitType = getSubmitType();

    if (submitType == SubmitType.REBASE_ALWAYS) {
      // REBASE_ALWAYS always forces a rebase, creating a new PatchSet (PatchSet 2)
      assertCurrentRevision(change2.getChangeId(), 2, masterHead);
      assertCurrentRevision(change1.getChangeId(), 2, masterParent);

      if (hasConcurrentCommit) {
        assertThat(masterGrandparent.getShortMessage()).isEqualTo("Concurrent Commit");
      }
    } else if (submitType == SubmitType.REBASE_IF_NECESSARY) {
      if (hasConcurrentCommit) {
        // Concurrent commit forces a rebase for both changes (PatchSet 2)
        assertCurrentRevision(change2.getChangeId(), 2, masterHead);
        assertCurrentRevision(change1.getChangeId(), 2, masterParent);
        assertThat(masterGrandparent.getShortMessage()).isEqualTo("Concurrent Commit");
      } else {
        // Without a concurrent commit, both changes can fast-forward (PatchSet 1)
        assertCurrentRevision(change2.getChangeId(), 1, masterHead);
        assertCurrentRevision(change1.getChangeId(), 1, masterParent);
        assertThat(masterHead.getId()).isEqualTo(change2.getCommit());
        assertThat(masterParent.getId()).isEqualTo(change1.getCommit());
      }
    }
  }
}
