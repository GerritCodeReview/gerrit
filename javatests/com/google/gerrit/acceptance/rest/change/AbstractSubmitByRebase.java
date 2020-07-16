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
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.common.ChangeInfo;
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
  public void submitWithRebaseMergeCommit() throws Throwable {
    /*
       *  (HEAD, origin/master, origin/HEAD) Merge changes X,Y
       |\
       | *   Merge branch 'master' into origin/master
       | |\
       | | * SHA Added a
       | |/
       * | Before
       |/
       * Initial empty repository
    */
    RevCommit initialHead = projectOperations.project(project).getHead("master");
    PushOneCommit.Result change1 = createChange("Added a", "a.txt", "");

    PushOneCommit change2Push =
        pushFactory.create(admin.newIdent(), testRepo, "Merge to master", "m.txt", "");
    change2Push.setParents(ImmutableList.of(initialHead, change1.getCommit()));
    PushOneCommit.Result change2 = change2Push.to("refs/for/master");

    testRepo.reset(initialHead);
    PushOneCommit.Result change3 = createChange("Before", "b.txt", "");

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

    assertThat(headParent2.getId()).isEqualTo(change2.getCommit().getId());
    assertThat(headParent2.getParentCount()).isEqualTo(2);

    RevCommit headGrandparent1 = parse(headParent2.getParent(0).getId());
    RevCommit headGrandparent2 = parse(headParent2.getParent(1).getId());

    assertThat(headGrandparent1.getId()).isEqualTo(initialHead.getId());
    assertThat(headGrandparent2.getId()).isEqualTo(change1.getCommit().getId());
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
        "Cannot rebase "
            + change2.getCommit().name()
            + ": The change could not be rebased due to a conflict during merge.");
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
        "Cannot rebase "
            + change2.getCommit().getName()
            + ": "
            + "The change could not be rebased due to a conflict during merge.");
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
}
